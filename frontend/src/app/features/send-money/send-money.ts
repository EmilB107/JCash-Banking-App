import { CurrencyPipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { finalize } from 'rxjs';
import { BalanceToggle } from '../../core/account/balance-toggle';
import {
  BalanceVisibilityService,
  MASKED_AMOUNT,
} from '../../core/account/balance-visibility.service';
import { AccountService } from '../../core/account/account.service';
import { amountErrorMessage, amountValidators } from '../../core/forms/amount';
import { MobileFormatDirective } from '../../core/forms/mobile-format.directive';
import { looksLikeMobile, stripSpaces } from '../../core/forms/mobile-number';
import { ApiError, toApiError } from '../../core/http/api-error';
import { TransferRequest, TransferResponse } from '../../core/transfers/transfer';
import { TransferService } from '../../core/transfers/transfer.service';

/** Friendlier wording for errors the user can fix; anything else shows the backend's message. */
const ERROR_MESSAGES: Record<string, string> = {
  RESOURCE_NOT_FOUND: 'No JCash account uses that email or mobile number.',
  SELF_TRANSFER_NOT_ALLOWED: "You can't send money to yourself.",
  INSUFFICIENT_FUNDS: "You don't have enough balance for this transfer.",
};

@Component({
  selector: 'app-send-money',
  imports: [ReactiveFormsModule, CurrencyPipe, BalanceToggle, MobileFormatDirective],
  templateUrl: './send-money.html',
})
export class SendMoney {
  private readonly transfers = inject(TransferService);
  protected readonly visibility = inject(BalanceVisibilityService);
  protected readonly masked = MASKED_AMOUNT;

  /** Also used for a quick "not enough balance" check; null until loaded. */
  protected readonly balance = signal<number | null>(null);

  protected readonly form = inject(NonNullableFormBuilder).group({
    recipient: ['', [Validators.required, Validators.maxLength(255)]],
    amount: ['', amountValidators()],
  });
  /** 'form' while filling in; 'review' shows the details for confirmation. */
  protected readonly step = signal<'form' | 'review'>('form');
  protected readonly sending = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly lastSent = signal<TransferResponse | null>(null);

  /**
   * The last transfer sent and its idempotency key. Retrying the same
   * recipient and amount after a failure reuses the key, so the money can't
   * move twice; different details or a completed transfer get a new key.
   */
  private attempt: { request: TransferRequest; key: string } | null = null;

  constructor() {
    // If this fails the page still works; the backend checks the balance anyway.
    inject(AccountService)
      .getMyAccount()
      .subscribe({ next: (account) => this.balance.set(account.balance), error: () => undefined });
  }

  protected review(): void {
    const amount = this.form.controls.amount;
    const balance = this.balance();
    // The backend has the final say; this just saves a round trip.
    if (amount.valid && balance !== null && Number(amount.value) > balance) {
      amount.setErrors({ insufficient: true });
    }
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.error.set(null);
    this.lastSent.set(null);
    this.step.set('review');
  }

  protected edit(): void {
    this.step.set('form');
  }

  protected confirm(): void {
    const { recipient, amount } = this.form.getRawValue();
    const request: TransferRequest = {
      recipient: looksLikeMobile(recipient) ? stripSpaces(recipient) : recipient.trim(),
      amount: Number(amount),
    };
    if (!this.attempt || !sameRequest(this.attempt.request, request)) {
      this.attempt = { request, key: crypto.randomUUID() };
    }
    this.sending.set(true);
    this.error.set(null);

    this.transfers
      .send(request, this.attempt.key)
      .pipe(finalize(() => this.sending.set(false)))
      .subscribe({
        next: (result) => {
          this.attempt = null;
          this.form.reset();
          this.step.set('form');
          this.lastSent.set(result);
          this.balance.set(result.senderBalanceAfter);
        },
        error: (err: unknown) => {
          this.step.set('form');
          this.error.set(messageFor(toApiError(err)));
        },
      });
  }

  /** The recipient as entered (mobile numbers keep their display spacing). */
  protected recipient(): string {
    return this.form.controls.recipient.value.trim();
  }

  protected amount(): number {
    return Number(this.form.controls.amount.value);
  }

  protected recipientError(): string | null {
    const control = this.form.controls.recipient;
    if (!control.touched || !control.errors) {
      return null;
    }
    return control.errors['required']
      ? 'Enter an email or mobile number.'
      : 'Use at most 255 characters.';
  }

  protected amountError(): string | null {
    const control = this.form.controls.amount;
    if (!control.touched || !control.errors) {
      return null;
    }
    return control.errors['insufficient']
      ? ERROR_MESSAGES['INSUFFICIENT_FUNDS']
      : amountErrorMessage(control.errors);
  }
}

function sameRequest(a: TransferRequest, b: TransferRequest): boolean {
  return a.recipient === b.recipient && a.amount === b.amount;
}

function messageFor(error: ApiError): string {
  return ERROR_MESSAGES[error.error] ?? error.message;
}

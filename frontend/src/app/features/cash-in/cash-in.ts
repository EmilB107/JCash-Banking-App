import { CurrencyPipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import { finalize } from 'rxjs';
import { BalanceToggle } from '../../core/account/balance-toggle';
import {
  BalanceVisibilityService,
  MASKED_AMOUNT,
} from '../../core/account/balance-visibility.service';
import { Account } from '../../core/account/account';
import { AccountService } from '../../core/account/account.service';
import { amountErrorMessage, amountValidators } from '../../core/forms/amount';
import { toApiError } from '../../core/http/api-error';

// Same per-request limit as the backend's CashInRequest.
export const MAX_CASH_IN = 100_000;

@Component({
  selector: 'app-cash-in',
  imports: [ReactiveFormsModule, CurrencyPipe, BalanceToggle],
  templateUrl: './cash-in.html',
})
export class CashIn {
  private readonly accounts = inject(AccountService);
  protected readonly visibility = inject(BalanceVisibilityService);
  protected readonly masked = MASKED_AMOUNT;

  protected readonly account = signal<Account | null>(null);

  protected readonly quickAmounts = [100, 500, 1000];
  protected readonly form = inject(NonNullableFormBuilder).group({
    amount: ['', amountValidators(MAX_CASH_IN)],
  });
  protected readonly submitting = signal(false);
  protected readonly error = signal<string | null>(null);
  /** Amount of the last successful cash-in, for the confirmation message. */
  protected readonly added = signal<number | null>(null);

  constructor() {
    this.accounts
      .getMyAccount()
      .subscribe({ next: (account) => this.account.set(account), error: () => undefined });
  }

  protected setAmount(amount: number): void {
    this.form.controls.amount.setValue(String(amount));
    this.form.controls.amount.markAsTouched();
  }

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.error.set(null);
    this.added.set(null);

    const amount = Number(this.form.getRawValue().amount);
    this.accounts
      .cashIn(amount)
      .pipe(finalize(() => this.submitting.set(false)))
      .subscribe({
        next: (account) => {
          this.added.set(amount);
          this.form.reset();
          this.account.set(account);
        },
        error: (err: unknown) => this.error.set(toApiError(err).message),
      });
  }

  protected amountError(): string | null {
    const control = this.form.controls.amount;
    return control.touched && control.errors ? amountErrorMessage(control.errors) : null;
  }
}

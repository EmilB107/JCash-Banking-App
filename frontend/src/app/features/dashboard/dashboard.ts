import { CurrencyPipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { Account } from '../../core/account/account';
import { AccountService } from '../../core/account/account.service';
import { BalanceToggle } from '../../core/account/balance-toggle';
import { onTabReturn } from '../../core/browser/tab-return';
import {
  BalanceVisibilityService,
  MASKED_AMOUNT,
} from '../../core/account/balance-visibility.service';
import { toApiError } from '../../core/http/api-error';
import { TransactionEntry } from '../../core/transactions/transaction';
import { TransactionSummary } from '../../core/transactions/transaction-summary';
import { TransactionService } from '../../core/transactions/transaction.service';

const RECENT_COUNT = 5;

@Component({
  selector: 'app-dashboard',
  imports: [CurrencyPipe, RouterLink, BalanceToggle, TransactionSummary],
  templateUrl: './dashboard.html',
})
export class Dashboard {
  private readonly accounts = inject(AccountService);
  private readonly transactions = inject(TransactionService);

  protected readonly visibility = inject(BalanceVisibilityService);
  protected readonly masked = MASKED_AMOUNT;
  protected readonly account = signal<Account | null>(null);
  protected readonly recent = signal<TransactionEntry[]>([]);
  protected readonly loadError = signal<string | null>(null);

  constructor() {
    this.load();
    onTabReturn(() => this.load());
  }

  /** Loads the balance and recent activity together; also used by the "Try again" button. */
  protected load(): void {
    this.loadError.set(null);
    forkJoin({
      account: this.accounts.getMyAccount(),
      history: this.transactions.getMyHistory(0, RECENT_COUNT),
    }).subscribe({
      next: ({ account, history }) => {
        this.account.set(account);
        this.recent.set(history.content);
      },
      error: (err: unknown) => this.loadError.set(toApiError(err).message),
    });
  }
}

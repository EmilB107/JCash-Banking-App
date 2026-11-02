import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Subject, catchError, combineLatest, map, of, startWith, switchMap, tap } from 'rxjs';
import {
  BalanceVisibilityService,
  MASKED_AMOUNT,
} from '../../core/account/balance-visibility.service';
import { toApiError } from '../../core/http/api-error';
import { TransactionHistoryPage } from '../../core/transactions/transaction';
import { TransactionSummary } from '../../core/transactions/transaction-summary';
import { TransactionService } from '../../core/transactions/transaction.service';

export const PAGE_SIZE = 20;

type State =
  | { status: 'loading' }
  | { status: 'error'; message: string }
  | { status: 'loaded'; history: TransactionHistoryPage };

@Component({
  selector: 'app-history',
  imports: [RouterLink, CurrencyPipe, DatePipe, TransactionSummary],
  templateUrl: './history.html',
})
export class History {
  protected readonly visibility = inject(BalanceVisibilityService);
  protected readonly masked = MASKED_AMOUNT;

  protected readonly state = signal<State>({ status: 'loading' });
  /** The page number shown to the user (1-based, as in the URL ?page=2). */
  protected readonly pageNumber = signal(1);
  /** Id of the transaction whose details are open, if any. */
  protected readonly expanded = signal<string | null>(null);

  private readonly retry$ = new Subject<void>();

  constructor() {
    const transactions = inject(TransactionService);

    // The page lives in the URL, so Back/Forward and reloads keep it.
    // switchMap cancels an older request if the user pages again before it finishes.
    combineLatest([
      inject(ActivatedRoute).queryParamMap.pipe(map((params) => toPageNumber(params.get('page')))),
      this.retry$.pipe(startWith(undefined)),
    ])
      .pipe(
        tap(([page]) => {
          this.pageNumber.set(page);
          this.expanded.set(null);
          this.state.set({ status: 'loading' });
        }),
        switchMap(([page]) =>
          // The API counts pages from 0.
          transactions.getMyHistory(page - 1, PAGE_SIZE).pipe(
            map((history): State => ({ status: 'loaded', history })),
            catchError((err: unknown) =>
              of<State>({ status: 'error', message: toApiError(err).message }),
            ),
          ),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((state) => this.state.set(state));
  }

  protected retry(): void {
    this.retry$.next();
  }

  protected toggle(id: string): void {
    this.expanded.update((current) => (current === id ? null : id));
  }

  /** Query params for a page link; page 1 drops the param to keep the URL clean. */
  protected pageParams(page: number): { page: number | null } {
    return { page: page > 1 ? page : null };
  }
}

/** Anything that isn't a whole number from 1 up (e.g. "abc", "0", "-3") means page 1. */
function toPageNumber(value: string | null): number {
  const page = Number(value);
  return Number.isInteger(page) && page >= 1 ? page : 1;
}

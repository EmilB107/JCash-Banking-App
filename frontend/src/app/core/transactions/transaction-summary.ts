import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, inject, input } from '@angular/core';
import { BalanceVisibilityService, MASKED_AMOUNT } from '../account/balance-visibility.service';
import { TransactionEntry } from './transaction';

@Component({
  selector: 'app-transaction-summary',
  imports: [CurrencyPipe, DatePipe],
  host: { class: 'flex w-full items-center justify-between gap-4' },
  template: `
    <div class="min-w-0">
      <p class="truncate text-sm font-medium text-ink-900">{{ entry().details }}</p>
      <p class="text-xs text-ink-600">{{ entry().createdAt | date: 'MMM d, y, h:mm a' }}</p>
    </div>
    <div class="shrink-0 text-right">
      @if (entry().direction === 'CREDIT') {
        <p class="text-sm font-semibold text-success-700">
          +{{ entry().amount | currency: entry().currency : 'symbol-narrow' }}
        </p>
      } @else {
        <p class="text-sm font-semibold text-ink-900">
          −{{ entry().amount | currency: entry().currency : 'symbol-narrow' }}
        </p>
      }
      <p class="text-xs text-ink-600">
        Balance
        {{
          visibility.hidden()
            ? masked
            : (entry().balanceAfter | currency: entry().currency : 'symbol-narrow')
        }}
      </p>
    </div>
  `,
})
export class TransactionSummary {
  readonly entry = input.required<TransactionEntry>();

  protected readonly visibility = inject(BalanceVisibilityService);
  protected readonly masked = MASKED_AMOUNT;
}

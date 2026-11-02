import { Component, inject } from '@angular/core';
import { LucideEye, LucideEyeOff } from '@lucide/angular';
import { BalanceVisibilityService } from './balance-visibility.service';

/**
 * Eye button that shows or hides balances. The icon uses the text color of
 * where it's placed, e.g. <app-balance-toggle class="text-ink-300" />.
 */
@Component({
  selector: 'app-balance-toggle',
  imports: [LucideEye, LucideEyeOff],
  template: `
    <button
      class="inline-flex rounded-full p-1.5 hover:bg-current/10 focus-visible:outline-2 focus-visible:outline-brand-500"
      type="button"
      [attr.aria-label]="visibility.hidden() ? 'Show balance' : 'Hide balance'"
      [attr.aria-pressed]="visibility.hidden()"
      (click)="visibility.toggle()"
    >
      @if (visibility.hidden()) {
        <svg lucideEyeOff size="20"></svg>
      } @else {
        <svg lucideEye size="20"></svg>
      }
    </button>
  `,
})
export class BalanceToggle {
  protected readonly visibility = inject(BalanceVisibilityService);
}

import { Injectable, signal } from '@angular/core';

const STORAGE_KEY = 'jcash.hideBalance';

/** Shown in place of an amount while balances are hidden. */
export const MASKED_AMOUNT = '₱••••••';

/**
 * Whether balances are hidden on screen (like banking apps' eye button).
 * Shared by every page that shows a balance, and remembered on this device.
 * It's only a display preference, so storage failures (e.g. private mode)
 * are ignored and balances simply start visible.
 */
@Injectable({ providedIn: 'root' })
export class BalanceVisibilityService {
  private readonly hiddenState = signal(readStored());

  readonly hidden = this.hiddenState.asReadonly();

  toggle(): void {
    const hidden = !this.hiddenState();
    this.hiddenState.set(hidden);
    try {
      localStorage.setItem(STORAGE_KEY, String(hidden));
    } catch {
      // Not saved; the setting still applies until the page is reloaded.
    }
  }
}

function readStored(): boolean {
  try {
    return localStorage.getItem(STORAGE_KEY) === 'true';
  } catch {
    return false;
  }
}

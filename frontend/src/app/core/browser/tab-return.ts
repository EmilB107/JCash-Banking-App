import { DOCUMENT } from '@angular/common';
import { DestroyRef, inject } from '@angular/core';

// Switching back to a tab usually fires both events; one reload is enough.
const DEBOUNCE_MS = 1000;

/**
 * Calls `refresh` when the user returns to the tab or window, so data changed
 * in another tab (each runs its own copy of the app) is reloaded. Call it in a
 * constructor; the listeners are removed when the component is destroyed.
 */
export function onTabReturn(refresh: () => void): void {
  const document = inject(DOCUMENT);
  const window = document.defaultView;
  let lastRun = 0;

  const listener = () => {
    if (document.visibilityState !== 'visible') {
      return;
    }
    const now = Date.now();
    if (now - lastRun < DEBOUNCE_MS) {
      return;
    }
    lastRun = now;
    refresh();
  };

  document.addEventListener('visibilitychange', listener);
  window?.addEventListener('focus', listener);
  inject(DestroyRef).onDestroy(() => {
    document.removeEventListener('visibilitychange', listener);
    window?.removeEventListener('focus', listener);
  });
}

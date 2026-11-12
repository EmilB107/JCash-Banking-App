import { HttpInterceptorFn } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { finalize } from 'rxjs';

// Longer than a normal request on the free plan, even a BCrypt login.
const SLOW_AFTER_MS = 5000;

/** Tracks API calls so the app can explain a long wait while the free-plan backend starts. */
@Injectable({ providedIn: 'root' })
export class ServerWakeService {
  private inFlight = 0;
  private timer: ReturnType<typeof setTimeout> | undefined;
  private readonly isSlow = signal(false);

  readonly slow = this.isSlow.asReadonly();

  requestStarted(): void {
    if (this.inFlight++ === 0) {
      this.timer = setTimeout(() => this.isSlow.set(true), SLOW_AFTER_MS);
    }
  }

  requestFinished(): void {
    if (--this.inFlight === 0) {
      clearTimeout(this.timer);
      this.isSlow.set(false);
    }
  }
}

export const serverWakeInterceptor: HttpInterceptorFn = (req, next) => {
  if (!req.url.startsWith('/api/')) {
    return next(req);
  }
  const wake = inject(ServerWakeService);
  wake.requestStarted();
  return next(req).pipe(finalize(() => wake.requestFinished()));
};

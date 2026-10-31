import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { switchMap } from 'rxjs';
import { CSRF_HEADER_NAME, CsrfService } from './csrf.service';

const SAFE_METHODS = new Set(['GET', 'HEAD', 'OPTIONS', 'TRACE']);

/**
 * Adds the X-XSRF-TOKEN header to state-changing requests to our own API.
 * Replaces Angular's built-in XSRF support (disabled in app.config.ts) because
 * that one never fetches a missing token.
 */
export const csrfInterceptor: HttpInterceptorFn = (req, next) => {
  if (SAFE_METHODS.has(req.method) || !req.url.startsWith('/api/')) {
    return next(req);
  }
  return inject(CsrfService)
    .token()
    .pipe(switchMap((token) => next(req.clone({ setHeaders: { [CSRF_HEADER_NAME]: token } }))));
};

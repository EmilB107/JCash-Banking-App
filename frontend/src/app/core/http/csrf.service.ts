import { DOCUMENT } from '@angular/common';
import { HttpBackend, HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, finalize, map, of, shareReplay } from 'rxjs';

const CSRF_COOKIE_NAME = 'XSRF-TOKEN';
export const CSRF_HEADER_NAME = 'X-XSRF-TOKEN';

interface CsrfResponse {
  token: string;
  headerName: string;
  parameterName: string;
}

/**
 * Supplies the CSRF token for state-changing requests.
 *
 * Spring writes the XSRF-TOKEN cookie lazily and clears it on logout, so the
 * cookie may be missing. In that case the token is fetched from
 * GET /api/auth/csrf (which also sets the cookie). Concurrent callers share one
 * in-flight fetch.
 */
@Injectable({ providedIn: 'root' })
export class CsrfService {
  private readonly document = inject(DOCUMENT);
  // Bypasses interceptors so fetching the token cannot recurse into the CSRF interceptor.
  private readonly http = new HttpClient(inject(HttpBackend));
  private pending: Observable<string> | null = null;

  token(): Observable<string> {
    const fromCookie = this.readCookie();
    if (fromCookie) {
      return of(fromCookie);
    }
    this.pending ??= this.http.get<CsrfResponse>('/api/auth/csrf').pipe(
      map((response) => response.token),
      finalize(() => (this.pending = null)),
      shareReplay(1),
    );
    return this.pending;
  }

  private readCookie(): string | null {
    for (const cookie of this.document.cookie.split(';')) {
      const [name, ...value] = cookie.trim().split('=');
      if (name === CSRF_COOKIE_NAME) {
        return decodeURIComponent(value.join('=')) || null;
      }
    }
    return null;
  }
}

import {
  provideHttpClient,
  withFetch,
  withInterceptors,
  withNoXsrfProtection,
} from '@angular/common/http';
import {
  ApplicationConfig,
  inject,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import { provideRouter } from '@angular/router';
import { catchError, of } from 'rxjs';
import { routes } from './app.routes';
import { AuthService } from './core/auth/auth.service';
import { sessionExpiredInterceptor } from './core/auth/session-expired.interceptor';
import { csrfInterceptor } from './core/http/csrf.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    provideHttpClient(
      withFetch(),
      withInterceptors([csrfInterceptor, sessionExpiredInterceptor]),
      // csrfInterceptor handles CSRF, including fetching a missing token.
      withNoXsrfProtection(),
    ),
    // Restore an existing session before the first route renders. If the
    // backend is unreachable, start anyway with the session state unknown.
    provideAppInitializer(() =>
      inject(AuthService)
        .loadCurrentUser()
        .pipe(catchError(() => of(null))),
    ),
  ],
};

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
import { routes } from './app.routes';
import { AuthService } from './core/auth/auth.service';
import { sessionExpiredInterceptor } from './core/auth/session-expired.interceptor';
import { csrfInterceptor } from './core/http/csrf.interceptor';
import { serverWakeInterceptor } from './core/http/server-wake';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    provideHttpClient(
      withFetch(),
      // serverWakeInterceptor is first so its timer includes any wait for a CSRF token.
      withInterceptors([serverWakeInterceptor, csrfInterceptor, sessionExpiredInterceptor]),
      // csrfInterceptor handles CSRF, including fetching a missing token.
      withNoXsrfProtection(),
    ),
    // Start restoring the session, but don't return it: waiting here would keep
    // every page blank for the minute a sleeping backend takes to start.
    provideAppInitializer(() => {
      inject(AuthService)
        .loadCurrentUser()
        .subscribe({ error: () => undefined });
    }),
  ],
};

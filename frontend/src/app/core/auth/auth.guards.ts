import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { catchError, map, of } from 'rxjs';
import { AuthService } from './auth.service';

export const authGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const toLogin = inject(Router).createUrlTree(['/login']);

  if (auth.currentUser()) {
    return true;
  }
  if (auth.currentUser() === null) {
    return toLogin;
  }
  // Still unknown: joins the startup check, or tries again if it failed (e.g. backend was down).
  return auth.loadCurrentUser().pipe(
    map((user) => (user ? true : toLogin)),
    catchError(() => of(toLogin)),
  );
};

// Doesn't wait for the session check, so these forms open even while the backend wakes up.
export const guestGuard: CanActivateFn = () =>
  inject(AuthService).isAuthenticated() ? inject(Router).createUrlTree(['/dashboard']) : true;

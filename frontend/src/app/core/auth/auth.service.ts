import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import {
  Observable,
  catchError,
  finalize,
  map,
  of,
  shareReplay,
  switchMap,
  tap,
  throwError,
} from 'rxjs';
import { RegisterRequest, UpdateProfileRequest, User } from './user';

/**
 * Holds the signed-in user for the server-side session.
 *
 * `currentUser` is `undefined` until the session has been checked, `null` when
 * signed out, and the user's profile when signed in.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly user = signal<User | null | undefined>(undefined);

  readonly currentUser = this.user.asReadonly();
  readonly isAuthenticated = computed(() => !!this.user());
  private sessionCheck: Observable<User | null> | null = null;

  /** Resolves to null (not an error) when there is no session. Concurrent callers share one check. */
  loadCurrentUser(): Observable<User | null> {
    this.sessionCheck ??= this.fetchMe().pipe(
      catchError((err: unknown) =>
        err instanceof HttpErrorResponse && err.status === 401 ? of(null) : throwError(() => err),
      ),
      tap((user) => this.user.set(user)),
      finalize(() => (this.sessionCheck = null)),
      shareReplay(1),
    );
    return this.sessionCheck;
  }

  /** Errors surface as HttpErrorResponse; convert with toApiError. */
  login(identifier: string, pin: string): Observable<User> {
    return this.sessionCheckSettled().pipe(
      switchMap(() => this.http.post<void>('/api/auth/login', { identifier, pin })),
      switchMap(() => this.fetchMe()),
      tap((user) => this.user.set(user)),
    );
  }

  /** Creates the user (and their PHP account), then logs them in. */
  register(request: RegisterRequest): Observable<User> {
    return this.http
      .post<User>('/api/users', request)
      .pipe(switchMap(() => this.login(request.email, request.pin)));
  }

  /**
   * Saves the profile and shows the saved values everywhere (e.g. the sidebar
   * name). The server keeps the session valid even when the email changes.
   */
  updateProfile(request: UpdateProfileRequest): Observable<User> {
    return this.http.put<User>('/api/users/me', request).pipe(tap((user) => this.user.set(user)));
  }

  /** A wrong current PIN fails with INCORRECT_PIN and counts toward the lockout. */
  changePin(currentPin: string, newPin: string): Observable<void> {
    return this.http.put<void>('/api/users/me/pin', { currentPin, newPin });
  }

  /** Local state is cleared even if the request fails. */
  logout(): Observable<void> {
    return this.http.post<void>('/api/auth/logout', null).pipe(
      map(() => undefined),
      finalize(() => this.clearSession()),
    );
  }

  /** Marks the user signed out without calling the server (e.g. session expired). */
  clearSession(): void {
    this.user.set(null);
  }

  // A 401 from a check that finishes after login would sign the user straight back out.
  private sessionCheckSettled(): Observable<unknown> {
    return this.sessionCheck?.pipe(catchError(() => of(null))) ?? of(null);
  }

  private fetchMe(): Observable<User> {
    return this.http.get<User>('/api/users/me');
  }
}

import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { toApiError } from '../http/api-error';
import { AuthService } from './auth.service';
import { sessionExpiredInterceptor } from './session-expired.interceptor';
import { User } from './user';

const USER: User = {
  id: '6f1c2a54-7a3e-4c4f-9a53-0e0f8c1d2b3a',
  email: 'demo@example.com',
  mobileNumber: '09171234567',
  displayName: 'Demo User',
  createdAt: '2026-10-31T00:00:00Z',
};

describe('AuthService', () => {
  let auth: AuthService;
  let backend: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([sessionExpiredInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    auth = TestBed.inject(AuthService);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('starts with the session state unknown', () => {
    expect(auth.currentUser()).toBeUndefined();
    expect(auth.isAuthenticated()).toBe(false);
  });

  it('restores an existing session', () => {
    auth.loadCurrentUser().subscribe();
    backend.expectOne('/api/users/me').flush(USER);

    expect(auth.currentUser()).toEqual(USER);
    expect(auth.isAuthenticated()).toBe(true);
  });

  it('treats 401 from /me as signed out', () => {
    let result: User | null | undefined;
    auth.loadCurrentUser().subscribe((user) => (result = user));
    backend
      .expectOne('/api/users/me')
      .flush(
        { error: 'UNAUTHORIZED', message: 'Login required' },
        { status: 401, statusText: 'Unauthorized' },
      );

    expect(result).toBeNull();
    expect(auth.currentUser()).toBeNull();
  });

  it('logs in and loads the profile', () => {
    auth.login('demo@example.com', '123456').subscribe();

    const login = backend.expectOne({ method: 'POST', url: '/api/auth/login' });
    expect(login.request.body).toEqual({ identifier: 'demo@example.com', pin: '123456' });
    login.flush({ message: 'Login successful' });
    backend.expectOne('/api/users/me').flush(USER);

    expect(auth.currentUser()).toEqual(USER);
  });

  it('surfaces login errors as ApiError without changing the session', () => {
    let error: unknown;
    auth.login('demo@example.com', '000000').subscribe({ error: (e) => (error = e) });
    backend
      .expectOne('/api/auth/login')
      .flush(
        { error: 'ACCOUNT_LOCKED', message: 'Account is locked' },
        { status: 423, statusText: 'Locked' },
      );

    expect(toApiError(error)).toEqual({ error: 'ACCOUNT_LOCKED', message: 'Account is locked' });
    expect(auth.currentUser()).toBeUndefined();
  });

  it('registers, then logs in with the same credentials', () => {
    auth
      .register({
        email: USER.email,
        mobileNumber: USER.mobileNumber,
        displayName: USER.displayName,
        pin: '123456',
      })
      .subscribe();

    backend
      .expectOne({ method: 'POST', url: '/api/users' })
      .flush(USER, { status: 201, statusText: 'Created' });
    const login = backend.expectOne('/api/auth/login');
    expect(login.request.body).toEqual({ identifier: USER.email, pin: '123456' });
    login.flush({ message: 'Login successful' });
    backend.expectOne('/api/users/me').flush(USER);

    expect(auth.currentUser()).toEqual(USER);
  });

  it('clears the session on logout, even if the request fails', () => {
    auth.loadCurrentUser().subscribe();
    backend.expectOne('/api/users/me').flush(USER);

    auth.logout().subscribe({ error: () => undefined });
    backend.expectOne('/api/auth/logout').flush(null, { status: 500, statusText: 'Server Error' });

    expect(auth.currentUser()).toBeNull();
  });

  it('clears the session when any API call returns 401', () => {
    auth.loadCurrentUser().subscribe();
    backend.expectOne('/api/users/me').flush(USER);

    TestBed.inject(HttpClient)
      .get('/api/accounts/me')
      .subscribe({ error: () => undefined });
    backend.expectOne('/api/accounts/me').flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(auth.currentUser()).toBeNull();
  });
});

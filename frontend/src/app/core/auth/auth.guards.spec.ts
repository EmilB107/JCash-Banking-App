import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  Router,
  RouterStateSnapshot,
  UrlTree,
  provideRouter,
} from '@angular/router';
import { Observable, firstValueFrom, isObservable } from 'rxjs';
import { authGuard, guestGuard } from './auth.guards';
import { AuthService } from './auth.service';
import { User } from './user';

const USER = { displayName: 'Demo' } as User;

function run(guard: typeof authGuard): Promise<boolean | UrlTree> {
  const result = TestBed.runInInjectionContext(() =>
    guard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot),
  );
  return isObservable(result)
    ? firstValueFrom(result as Observable<boolean | UrlTree>)
    : Promise.resolve(result as boolean | UrlTree);
}

describe('route guards', () => {
  let auth: AuthService;
  let backend: HttpTestingController;
  let router: Router;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    auth = TestBed.inject(AuthService);
    backend = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
  });

  function signIn(): void {
    auth.loadCurrentUser().subscribe();
    backend.expectOne('/api/users/me').flush(USER);
  }

  it('authGuard lets signed-in users through', async () => {
    signIn();
    expect(await run(authGuard)).toBe(true);
  });

  it('authGuard sends signed-out users to /login', async () => {
    auth.clearSession();
    const result = await run(authGuard);
    expect(router.serializeUrl(result as UrlTree)).toBe('/login');
  });

  it('authGuard re-checks the session when it is still unknown', async () => {
    const result = run(authGuard);
    backend.expectOne('/api/users/me').flush(USER);
    expect(await result).toBe(true);
  });

  it('authGuard joins a session check that is already running', async () => {
    auth.loadCurrentUser().subscribe();
    const result = run(authGuard);
    backend.expectOne('/api/users/me').flush(USER);
    expect(await result).toBe(true);
  });

  it('guestGuard does not wait while the session is still unknown', async () => {
    auth.loadCurrentUser().subscribe();
    expect(await run(guestGuard)).toBe(true);
  });

  it('guestGuard sends signed-in users to /dashboard', async () => {
    signIn();
    const result = await run(guestGuard);
    expect(router.serializeUrl(result as UrlTree)).toBe('/dashboard');
  });

  it('guestGuard lets signed-out users through', async () => {
    auth.clearSession();
    expect(await run(guestGuard)).toBe(true);
  });
});

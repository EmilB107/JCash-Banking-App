import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { csrfInterceptor } from './csrf.interceptor';
import { CSRF_HEADER_NAME } from './csrf.service';

function setCsrfCookie(value: string | null): void {
  document.cookie =
    value === null
      ? 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
      : `XSRF-TOKEN=${value}; path=/`;
}

describe('csrfInterceptor', () => {
  let http: HttpClient;
  let backend: HttpTestingController;

  beforeEach(() => {
    setCsrfCookie(null);
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([csrfInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    backend.verify();
    setCsrfCookie(null);
  });

  it('does not add a token to GET requests', () => {
    http.get('/api/users/me').subscribe();

    const req = backend.expectOne('/api/users/me');
    expect(req.request.headers.has(CSRF_HEADER_NAME)).toBe(false);
    req.flush({});
  });

  it('uses the XSRF-TOKEN cookie when present', () => {
    setCsrfCookie('cookie-token');

    http.post('/api/transfers', {}).subscribe();

    const req = backend.expectOne('/api/transfers');
    expect(req.request.headers.get(CSRF_HEADER_NAME)).toBe('cookie-token');
    req.flush({});
  });

  it('fetches a token when the cookie is missing', () => {
    http.post('/api/auth/login', {}).subscribe();

    backend
      .expectOne({ method: 'GET', url: '/api/auth/csrf' })
      .flush({ token: 'fetched-token', headerName: CSRF_HEADER_NAME, parameterName: '_csrf' });

    const req = backend.expectOne('/api/auth/login');
    expect(req.request.headers.get(CSRF_HEADER_NAME)).toBe('fetched-token');
    req.flush({});
  });

  it('shares one token fetch between concurrent requests', () => {
    http.post('/api/auth/login', {}).subscribe();
    http.post('/api/users', {}).subscribe();

    backend
      .expectOne('/api/auth/csrf')
      .flush({ token: 'shared-token', headerName: CSRF_HEADER_NAME, parameterName: '_csrf' });

    for (const url of ['/api/auth/login', '/api/users']) {
      const req = backend.expectOne(url);
      expect(req.request.headers.get(CSRF_HEADER_NAME)).toBe('shared-token');
      req.flush({});
    }
  });

  it('never sends the token to other origins', () => {
    setCsrfCookie('cookie-token');

    http.post('https://example.com/api/collect', {}).subscribe();

    const req = backend.expectOne('https://example.com/api/collect');
    expect(req.request.headers.has(CSRF_HEADER_NAME)).toBe(false);
    req.flush({});
  });
});

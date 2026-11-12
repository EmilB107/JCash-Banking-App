import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ServerWakeService, serverWakeInterceptor } from './server-wake';

describe('serverWakeInterceptor', () => {
  let http: HttpClient;
  let backend: HttpTestingController;
  let wake: ServerWakeService;

  beforeEach(() => {
    vi.useFakeTimers();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([serverWakeInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
    wake = TestBed.inject(ServerWakeService);
  });

  afterEach(() => vi.useRealTimers());

  it('stays quiet for a request that finishes quickly', () => {
    http.get('/api/health').subscribe();
    vi.advanceTimersByTime(1000);
    backend.expectOne('/api/health').flush({});
    vi.advanceTimersByTime(10000);

    expect(wake.slow()).toBe(false);
  });

  it('reports a slow request until it finishes', () => {
    http.get('/api/users/me').subscribe();
    vi.advanceTimersByTime(5000);
    expect(wake.slow()).toBe(true);

    backend.expectOne('/api/users/me').flush({});
    expect(wake.slow()).toBe(false);
  });

  it('stays slow until the last overlapping request finishes', () => {
    http.get('/api/a').subscribe();
    http.get('/api/b').subscribe();
    vi.advanceTimersByTime(5000);

    backend.expectOne('/api/a').flush({});
    expect(wake.slow()).toBe(true);

    backend.expectOne('/api/b').flush({});
    expect(wake.slow()).toBe(false);
  });

  it('ignores requests that are not to the API', () => {
    http.get('/jcash-icon.svg').subscribe();
    vi.advanceTimersByTime(5000);

    expect(wake.slow()).toBe(false);
    backend.expectOne('/jcash-icon.svg').flush('');
  });
});

import { Component, computed, effect, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterOutlet } from '@angular/router';
import { filter, map } from 'rxjs';
import { AuthService } from './core/auth/auth.service';
import { ServerWakeService } from './core/http/server-wake';

@Component({
  imports: [RouterOutlet],
  selector: 'app-root',
  styleUrl: './app.css',
  templateUrl: './app.html',
})
export class App {
  private readonly wake = inject(ServerWakeService);
  private readonly router = inject(Router);

  // Undefined until the first page opens, which is the wait on a link to a signed-in page.
  private readonly url = toSignal(
    this.router.events.pipe(
      filter((event) => event instanceof NavigationEnd),
      map((event) => event.urlAfterRedirects),
    ),
  );

  // The landing page works without the backend, so it has nothing to apologise for.
  protected readonly showWakeNotice = computed(() => this.wake.slow() && this.url() !== '/');

  constructor() {
    const auth = inject(AuthService);
    let previous = auth.currentUser();

    effect(() => {
      const user = auth.currentUser();
      // Whenever a signed-in user becomes signed out (logout button, or the
      // session expired and an API call returned 401), go to the login page.
      if (previous && user === null) {
        void this.router.navigateByUrl('/login');
      }
      // guestGuard doesn't wait for the session check, so it may find a session after the form opened.
      if (previous === undefined && user && ['/login', '/register'].includes(this.router.url)) {
        void this.router.navigateByUrl('/dashboard');
      }
      previous = user;
    });
  }
}

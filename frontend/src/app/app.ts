import { Component, effect, inject } from '@angular/core';
import { Router, RouterOutlet } from '@angular/router';
import { AuthService } from './core/auth/auth.service';

@Component({
  imports: [RouterOutlet],
  selector: 'app-root',
  styleUrl: './app.css',
  templateUrl: './app.html',
})
export class App {
  constructor() {
    const auth = inject(AuthService);
    const router = inject(Router);
    let previous = auth.currentUser();

    // Whenever a signed-in user becomes signed out (logout button, or the
    // session expired and an API call returned 401), go to the login page.
    effect(() => {
      const user = auth.currentUser();
      if (previous && user === null) {
        void router.navigateByUrl('/login');
      }
      previous = user;
    });
  }
}

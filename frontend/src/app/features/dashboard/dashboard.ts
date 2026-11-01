import { Component, inject } from '@angular/core';
import { AuthService } from '../../core/auth/auth.service';

/** Placeholder until step 3 (balance, cash-in, recent activity). */
@Component({
  selector: 'app-dashboard',
  template: `
    <main class="mx-auto max-w-2xl px-4 py-10">
      <div class="flex items-center justify-between gap-4">
        <h1 class="text-2xl font-semibold">Hi, {{ auth.currentUser()?.displayName }}</h1>
        <button class="btn-secondary px-3 py-2 text-sm" type="button" (click)="logout()">
          Log out
        </button>
      </div>
      <p class="mt-4 text-ink-600">Your balance and recent activity will appear here.</p>
    </main>
  `,
})
export class Dashboard {
  protected readonly auth = inject(AuthService);

  // Navigation to /login happens in App once the user becomes signed out.
  protected logout(): void {
    this.auth.logout().subscribe({ error: () => undefined });
  }
}

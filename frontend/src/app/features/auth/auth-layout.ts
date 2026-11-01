import { Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-auth-layout',
  imports: [RouterLink],
  template: `
    <main class="flex min-h-dvh flex-col items-center justify-center px-4 py-12">
      <div class="w-full max-w-sm">
        <a class="block text-center text-3xl font-bold tracking-tight text-brand-500" routerLink="/"
          >JCash</a
        >
        <div class="mt-6 rounded-2xl bg-white p-6 shadow-sm ring-1 ring-ink-200 sm:p-8">
          <h1 class="text-xl font-semibold text-ink-900">{{ heading() }}</h1>
          <ng-content />
        </div>
        <p class="mt-6 text-center text-xs text-ink-600">
          Simulated wallet for demonstration only. No real money is involved.
        </p>
      </div>
    </main>
  `,
})
export class AuthLayout {
  readonly heading = input.required<string>();
}

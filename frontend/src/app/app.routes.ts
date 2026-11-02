import { Routes } from '@angular/router';
import { authGuard, guestGuard } from './core/auth/auth.guards';

// loadComponent lazy-loads each page: its code is downloaded only when first visited.
export const routes: Routes = [
  // No guard: the landing page is open to everyone (signed-in users see "Go to dashboard").
  {
    path: '',
    pathMatch: 'full',
    title: 'JCash · Simulated e-wallet',
    loadComponent: () => import('./features/landing/landing').then((m) => m.Landing),
  },
  {
    path: 'login',
    title: 'Log in · JCash',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/auth/login/login').then((m) => m.Login),
  },
  {
    path: 'register',
    title: 'Create account · JCash',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/auth/register/register').then((m) => m.Register),
  },
  // Signed-in pages share the Shell layout (sidebar); each child renders in its <router-outlet>.
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () => import('./features/shell/shell').then((m) => m.Shell),
    children: [
      {
        path: 'dashboard',
        title: 'Dashboard · JCash',
        loadComponent: () => import('./features/dashboard/dashboard').then((m) => m.Dashboard),
      },
      {
        path: 'cash-in',
        title: 'Cash in · JCash',
        loadComponent: () => import('./features/cash-in/cash-in').then((m) => m.CashIn),
      },
      {
        path: 'send',
        title: 'Send money · JCash',
        loadComponent: () => import('./features/send-money/send-money').then((m) => m.SendMoney),
      },
      {
        path: 'history',
        title: 'Transaction history · JCash',
        loadComponent: () => import('./features/history/history').then((m) => m.History),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];

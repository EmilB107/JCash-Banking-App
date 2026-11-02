import { Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import {
  LucideCirclePlus,
  LucideClock,
  LucideDynamicIcon,
  LucideHouse,
  LucideIcon,
  LucideSend,
  LucideUser,
} from '@lucide/angular';
import { AuthService } from '../../core/auth/auth.service';

interface NavItem {
  label: string;
  /** null for pages that don't exist yet; they show greyed out. */
  path: string | null;
  /** Lucide icon (https://lucide.dev/icons); drawn in the link's text color. */
  icon: LucideIcon;
}

/** Layout for signed-in pages: top header, sidebar (stacked on phones), and the page in <router-outlet>. */
@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, LucideDynamicIcon],
  templateUrl: './shell.html',
})
export class Shell {
  protected readonly auth = inject(AuthService);

  protected readonly nav: NavItem[] = [
    {
      label: 'Dashboard',
      path: '/dashboard',
      icon: LucideHouse,
    },
    {
      label: 'Cash in',
      path: '/cash-in',
      icon: LucideCirclePlus,
    },
    {
      label: 'Send money',
      path: '/send',
      icon: LucideSend,
    },
    {
      label: 'Transaction history',
      path: null,
      icon: LucideClock,
    },
    {
      label: 'Profile',
      path: null,
      icon: LucideUser,
    },
  ];

  // Navigation to /login happens in App once the user becomes signed out.
  protected logout(): void {
    this.auth.logout().subscribe({ error: () => undefined });
  }
}

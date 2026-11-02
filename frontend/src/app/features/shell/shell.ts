import { DOCUMENT } from '@angular/common';
import {
  Component,
  ElementRef,
  afterNextRender,
  effect,
  inject,
  Injector,
  signal,
  viewChild,
} from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import {
  LucideCirclePlus,
  LucideClock,
  LucideDynamicIcon,
  LucideHouse,
  LucideIcon,
  LucideMenu,
  LucideSend,
  LucideUser,
  LucideX,
} from '@lucide/angular';
import { AuthService } from '../../core/auth/auth.service';

interface NavItem {
  label: string;
  /** null for pages that don't exist yet; they show greyed out. */
  path: string | null;
  /** Lucide icon (https://lucide.dev/icons); drawn in the link's text color. */
  icon: LucideIcon;
}

/** Layout for signed-in pages: top header, sidebar (a drawer on phones), and the page in <router-outlet>. */
@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, LucideDynamicIcon, LucideMenu, LucideX],
  host: { '(document:keydown.escape)': 'closeMenu()' },
  templateUrl: './shell.html',
})
export class Shell {
  protected readonly auth = inject(AuthService);
  private readonly injector = inject(Injector);

  /** Phones only: whether the sidebar drawer is open. Ignored from md up. */
  protected readonly menuOpen = signal(false);
  private readonly menuButton = viewChild.required<ElementRef<HTMLButtonElement>>('menuButton');
  private readonly closeButton = viewChild.required<ElementRef<HTMLButtonElement>>('closeButton');

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

  constructor() {
    // Stop the page behind the open drawer from scrolling.
    const body = inject(DOCUMENT).body;
    effect((onCleanup) => {
      body.classList.toggle('overflow-hidden', this.menuOpen());
      onCleanup(() => body.classList.remove('overflow-hidden'));
    });
  }

  protected openMenu(): void {
    this.menuOpen.set(true);
    // Move keyboard focus into the drawer once it's rendered visible.
    afterNextRender(() => this.closeButton().nativeElement.focus(), { injector: this.injector });
  }

  /**
   * `restoreFocus` returns focus to the menu button (for ✕, the backdrop and
   * Escape). Picking a page passes false so focus isn't pulled away from it.
   */
  protected closeMenu(restoreFocus = true): void {
    if (!this.menuOpen()) {
      return;
    }
    this.menuOpen.set(false);
    if (restoreFocus) {
      this.menuButton().nativeElement.focus();
    }
  }

  // Navigation to /login happens in App once the user becomes signed out.
  protected logout(): void {
    this.auth.logout().subscribe({ error: () => undefined });
  }
}

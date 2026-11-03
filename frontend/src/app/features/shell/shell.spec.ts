import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { Shell } from './shell';

describe('Shell', () => {
  function setup(displayName: string) {
    TestBed.configureTestingModule({
      imports: [Shell],
      providers: [
        // Any URL matches, so clicking a nav link can finish navigating.
        provideRouter([{ path: '**', children: [] }]),
        { provide: AuthService, useValue: { currentUser: () => ({ displayName }) } },
      ],
    });
    const fixture = TestBed.createComponent(Shell);
    fixture.detectChanges();
    return fixture;
  }

  it('greets the user by their full name', () => {
    const el = setup('Juan Carlos Dela Cruz').nativeElement as HTMLElement;
    expect(el.querySelector('aside')!.textContent).toContain('Juan Carlos Dela Cruz');
  });

  it('links to every page', () => {
    const el = setup('Juan').nativeElement as HTMLElement;
    const links = [...el.querySelectorAll('nav[aria-label=Main] a')].map((a) =>
      a.getAttribute('href'),
    );
    expect(links).toEqual(['/dashboard', '/cash-in', '/send', '/history', '/profile']);
  });

  describe('mobile menu', () => {
    function open() {
      const fixture = setup('Juan');
      const el = fixture.nativeElement as HTMLElement;
      const menuButton = el.querySelector<HTMLButtonElement>('[aria-label="Open menu"]')!;
      menuButton.click();
      fixture.detectChanges();
      return { fixture, el, menuButton };
    }

    it('opens the drawer, moves focus into it, and locks page scrolling', () => {
      const { el, menuButton } = open();

      expect(menuButton.getAttribute('aria-expanded')).toBe('true');
      expect(document.activeElement).toBe(el.querySelector('[aria-label="Close menu"]'));
      expect(document.body.classList).toContain('overflow-hidden');
    });

    it('closes with the close button and returns focus to the menu button', () => {
      const { fixture, el, menuButton } = open();
      el.querySelector<HTMLButtonElement>('[aria-label="Close menu"]')!.click();
      fixture.detectChanges();

      expect(menuButton.getAttribute('aria-expanded')).toBe('false');
      expect(document.activeElement).toBe(menuButton);
      expect(document.body.classList).not.toContain('overflow-hidden');
    });

    it('closes with Escape', () => {
      const { fixture, menuButton } = open();
      document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
      fixture.detectChanges();

      expect(menuButton.getAttribute('aria-expanded')).toBe('false');
    });

    it('closes when a page is picked', async () => {
      const { fixture, el, menuButton } = open();
      el.querySelector<HTMLAnchorElement>('a[href="/send"]')!.click();
      await fixture.whenStable();
      fixture.detectChanges();

      expect(menuButton.getAttribute('aria-expanded')).toBe('false');
    });
  });
});

import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { Landing } from './landing';

describe('Landing', () => {
  function render(signedIn: boolean): HTMLElement {
    TestBed.configureTestingModule({
      imports: [Landing],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: { isAuthenticated: signal(signedIn) } },
      ],
    });
    const fixture = TestBed.createComponent(Landing);
    fixture.detectChanges();
    return fixture.nativeElement;
  }

  function links(el: HTMLElement): (string | null)[] {
    return Array.from(el.querySelectorAll('a')).map((a) => a.getAttribute('href'));
  }

  it('shows visitors the disclaimer and links to register and login', () => {
    const el = render(false);

    expect(el.textContent).toContain('no real money is involved');
    expect(links(el)).toContain('/register');
    expect(links(el)).toContain('/login');
    expect(links(el)).not.toContain('/dashboard');
    expect(el.textContent).toContain('Try it out');
  });

  it('shows signed-in users a dashboard link instead of login and register', () => {
    const el = render(true);

    expect(links(el)).toContain('/dashboard');
    expect(links(el)).not.toContain('/register');
    expect(links(el)).not.toContain('/login');
    expect(el.textContent).not.toContain('Try it out');
  });
});

import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { Shell } from './shell';

describe('Shell', () => {
  function setup(displayName: string) {
    TestBed.configureTestingModule({
      imports: [Shell],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: { currentUser: () => ({ displayName }) } },
      ],
    });
    const fixture = TestBed.createComponent(Shell);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('greets the user by their full name', () => {
    const el = setup('Juan Carlos Dela Cruz');
    expect(el.querySelector('aside')!.textContent).toContain('Juan Carlos Dela Cruz');
  });

  it('links to built pages and shows upcoming ones as disabled', () => {
    const el = setup('Juan');
    const links = [...el.querySelectorAll('nav[aria-label=Main] a')].map((a) =>
      a.getAttribute('href'),
    );
    expect(links).toEqual(['/dashboard', '/cash-in', '/send']);

    const disabled = [...el.querySelectorAll('[aria-disabled=true]')].map((s) =>
      s.textContent!.trim(),
    );
    expect(disabled).toEqual(['Transaction history', 'Profile']);
  });
});

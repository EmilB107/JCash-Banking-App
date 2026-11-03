import { HttpErrorResponse } from '@angular/common/http';
import { ApplicationRef, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { of, throwError } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { User } from '../../core/auth/user';
import { Profile } from './profile';

const USER: User = {
  id: 'u1',
  email: 'juan@example.com',
  mobileNumber: '09171234567',
  displayName: 'Juan Dela Cruz',
  createdAt: '2026-10-31T04:00:00Z',
};

function apiError(status: number, error: string, message: string) {
  return throwError(() => new HttpErrorResponse({ status, error: { error, message } }));
}

describe('Profile', () => {
  const updateProfile = vi.fn();

  function setup() {
    // A real signal, so the view updates when the fake updateProfile "saves".
    const currentUser = signal<User | null>(USER);
    updateProfile.mockImplementation((request: Partial<User>) => {
      currentUser.set({ ...USER, ...request });
      return of(currentUser());
    });
    TestBed.configureTestingModule({
      imports: [Profile],
      providers: [
        provideRouter([]),
        {
          provide: AuthService,
          useValue: { currentUser: currentUser.asReadonly(), updateProfile },
        },
      ],
    });
    const fixture = TestBed.createComponent(Profile);
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;
    const type = (id: string, value: string) => {
      const input = el.querySelector<HTMLInputElement>(`#${id}`)!;
      input.value = value;
      input.dispatchEvent(new Event('input'));
    };
    const click = (label: string) => {
      [...el.querySelectorAll('button')].find((b) => b.textContent!.trim() === label)!.click();
      fixture.detectChanges();
    };
    const submit = () => {
      el.querySelector('form')!.dispatchEvent(new Event('submit'));
      fixture.detectChanges();
    };
    return { el, type, click, submit };
  }

  beforeEach(() => {
    updateProfile.mockReset();
  });

  it("shows the signed-in user's details", () => {
    const { el } = setup();
    const text = el.textContent!;

    expect(text).toContain('Juan Dela Cruz');
    expect(text).toContain('juan@example.com');
    expect(text).toContain('0917 123 4567');
    expect(text).toContain('October 31, 2026');
  });

  describe('edit profile', () => {
    it('starts with the current values and saves trimmed ones', () => {
      const { el, type, click, submit } = setup();
      click('Edit');

      expect(el.querySelector<HTMLInputElement>('#displayName')!.value).toBe('Juan Dela Cruz');
      expect(el.querySelector<HTMLInputElement>('#mobileNumber')!.value).toBe('0917 123 4567');

      type('displayName', '  Juan D. Cruz ');
      type('mobileNumber', '+639181234567');
      submit();

      expect(updateProfile).toHaveBeenCalledWith({
        displayName: 'Juan D. Cruz',
        email: 'juan@example.com',
        mobileNumber: '+639181234567',
      });
      expect(el.querySelector('#displayName')).toBeNull();
      expect(el.textContent).toContain('Your details were updated.');
      expect(el.textContent).toContain('Juan D. Cruz');
    });

    it('rejects invalid values without calling the API', () => {
      const { el, type, click, submit } = setup();
      click('Edit');
      type('email', 'juan@example');
      type('mobileNumber', '12345');
      type('displayName', '');
      submit();

      expect(updateProfile).not.toHaveBeenCalled();
      expect(el.textContent).toContain('Enter a valid email address.');
      expect(el.textContent).toContain('Use 0917 123 4567 or +63 917 123 4567.');
      expect(el.textContent).toContain('Enter your name.');
    });

    it('shows "already registered" on the email field', () => {
      const { el, type, click, submit } = setup();
      updateProfile.mockReturnValue(
        apiError(409, 'EMAIL_ALREADY_REGISTERED', 'Email is already registered: juan@example.com'),
      );
      click('Edit');
      type('email', 'taken@example.com');
      submit();

      const field = el.querySelector('#email')!.parentElement!;
      expect(field.textContent).toContain('Email is already registered');
      expect(el.querySelector('#email')!.getAttribute('aria-invalid')).toBe('true');
    });

    it('moves focus into the form and back to Edit when it closes', () => {
      const { el, click } = setup();
      click('Edit');
      expect(document.activeElement).toBe(el.querySelector('#displayName'));

      click('Cancel');
      expect(document.activeElement!.textContent!.trim()).toBe('Edit');
    });

    it('links a field to its error message for screen readers', () => {
      const { el, type, click, submit } = setup();
      click('Edit');
      const email = el.querySelector('#email')!;
      expect(email.getAttribute('aria-describedby')).toBeNull();

      type('email', 'juan@example');
      submit();
      expect(email.getAttribute('aria-describedby')).toBe('email-error');
      expect(el.querySelector('#email-error')!.textContent).toContain('Enter a valid email');
    });

    it('enables Save only when a value would actually change', () => {
      const { el, type, click, submit } = setup();
      click('Edit');
      const save = [...el.querySelectorAll('button')].find(
        (b) => b.textContent!.trim() === 'Save changes',
      )!;
      const refresh = () => TestBed.inject(ApplicationRef).tick();
      expect(save.disabled).toBe(true);

      // Same values as stored, just typed differently.
      type('email', ' JUAN@example.com ');
      type('mobileNumber', '+639171234567');
      refresh();
      expect(save.disabled).toBe(true);

      type('displayName', 'Juan D. Cruz');
      refresh();
      expect(save.disabled).toBe(false);

      type('displayName', 'Juan Dela Cruz');
      refresh();
      expect(save.disabled).toBe(true);

      submit();
      expect(updateProfile).not.toHaveBeenCalled();
    });

    it('discards unsaved edits on cancel', () => {
      const { el, type, click } = setup();
      click('Edit');
      type('displayName', 'Someone Else');
      click('Cancel');

      expect(el.textContent).toContain('Juan Dela Cruz');
      click('Edit');
      expect(el.querySelector<HTMLInputElement>('#displayName')!.value).toBe('Juan Dela Cruz');
    });
  });

  describe('change PIN', () => {
    it('links to the change-PIN page', () => {
      const { el } = setup();
      const link = [...el.querySelectorAll('a')].find(
        (a) => a.textContent!.trim() === 'Change PIN',
      );
      expect(link!.getAttribute('href')).toBe('/profile/pin');
    });

    it('confirms a PIN change only when sent back from the change-PIN page', async () => {
      TestBed.configureTestingModule({
        providers: [
          provideRouter([
            { path: 'profile', component: Profile },
            { path: 'dashboard', children: [] },
          ]),
          { provide: AuthService, useValue: { currentUser: signal(USER).asReadonly() } },
        ],
      });
      const harness = await RouterTestingHarness.create();
      const router = TestBed.inject(Router);

      await router.navigateByUrl('/profile', { state: { pinChanged: true } });
      harness.detectChanges();
      expect(harness.routeNativeElement!.textContent).toContain('Your PIN was changed.');

      // A plain visit (e.g. from the sidebar) doesn't repeat the message.
      await router.navigateByUrl('/dashboard');
      await router.navigateByUrl('/profile');
      harness.detectChanges();
      expect(harness.routeNativeElement!.textContent).not.toContain('Your PIN was changed.');
    });
  });
});

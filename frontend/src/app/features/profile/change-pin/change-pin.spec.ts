import { ApplicationRef } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { AuthService } from '../../../core/auth/auth.service';
import { PinVisibility } from '../../../core/forms/pin-field';
import { ChangePin } from './change-pin';

function apiError(status: number, error: string, message: string) {
  return throwError(() => new HttpErrorResponse({ status, error: { error, message } }));
}

describe('ChangePin', () => {
  const changePin = vi.fn();

  function setup() {
    TestBed.configureTestingModule({
      imports: [ChangePin],
      providers: [provideRouter([]), { provide: AuthService, useValue: { changePin } }],
    });
    const fixture = TestBed.createComponent(ChangePin);
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;
    const type = (id: string, value: string) => {
      const input = el.querySelector<HTMLInputElement>(`#${id}`)!;
      input.value = value;
      input.dispatchEvent(new Event('input'));
    };
    const fill = (current: string, next: string) => {
      type('currentPin', current);
      type('newPin', next);
      type('confirmPin', next);
    };
    const click = (label: string) => {
      [...el.querySelectorAll('button')].find((b) => b.textContent!.trim() === label)!.click();
      fixture.detectChanges();
    };
    const submit = () => {
      el.querySelector('form')!.dispatchEvent(new Event('submit'));
      fixture.detectChanges();
    };
    return { el, type, fill, click, submit };
  }

  beforeEach(() => changePin.mockReset());

  it('starts with the cursor in Current PIN and links back to the profile', () => {
    const { el } = setup();

    expect(document.activeElement).toBe(el.querySelector('#currentPin'));
    const hrefs = [...el.querySelectorAll('a')].map((a) => a.getAttribute('href'));
    expect(hrefs).toEqual(['/profile', '/profile']); // "Back to profile" and Cancel
  });

  it('changes the PIN and returns to the profile with a confirmation', () => {
    changePin.mockReturnValue(of(undefined));
    const { fill, submit } = setup();
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    fill('123456', '246810');
    submit();

    expect(changePin).toHaveBeenCalledWith('123456', '246810');
    expect(navigate).toHaveBeenCalledWith('/profile', { state: { pinChanged: true } });
  });

  it('rejects mismatched or unchanged PINs without calling the API', () => {
    const { el, fill, type, submit } = setup();
    fill('123456', '246810');
    type('confirmPin', '111111');
    submit();
    expect(el.textContent).toContain("PINs don't match.");

    fill('123456', '123456');
    submit();
    expect(el.textContent).toContain('Choose a PIN different from your current one.');

    expect(changePin).not.toHaveBeenCalled();
  });

  it('shows a wrong current PIN on that field, linked for screen readers', () => {
    changePin.mockReturnValue(
      apiError(400, 'INCORRECT_PIN', 'Current PIN is incorrect. 2 attempts left.'),
    );
    const { el, fill, submit } = setup();
    fill('654321', '246810');
    submit();

    expect(el.querySelector('#currentPin')!.getAttribute('aria-describedby')).toBe(
      'currentPin-error',
    );
    expect(el.querySelector('#currentPin-error')!.textContent).toContain(
      'Current PIN is incorrect. 2 attempts left.',
    );
  });

  it('shows a lockout above the form and stays on the page', () => {
    changePin.mockReturnValue(apiError(423, 'ACCOUNT_LOCKED', 'Account is locked.'));
    const { el, fill, submit } = setup();
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl');
    fill('654321', '246810');
    submit();

    expect(el.querySelector('[role=alert]')!.textContent).toContain('Account is locked.');
    expect(navigate).not.toHaveBeenCalled();
  });

  it('shows or hides all PIN fields together, with eyes only on filled fields', () => {
    const { el, type } = setup();
    const app = TestBed.inject(ApplicationRef);
    const types = () =>
      [...el.querySelectorAll<HTMLInputElement>('#currentPin, #newPin, #confirmPin')].map(
        (input) => input.type,
      );
    const eyes = () => el.querySelectorAll<HTMLButtonElement>('app-pin-field button');
    expect(eyes()).toHaveLength(0);

    type('currentPin', '123456');
    type('newPin', '24');
    app.tick();
    expect(eyes()).toHaveLength(2);

    eyes()[1].click();
    app.tick();
    expect(types()).toEqual(['text', 'text', 'text']);
    expect([...eyes()].map((b) => b.getAttribute('aria-label'))).toEqual([
      'Hide PINs',
      'Hide PINs',
    ]);

    eyes()[0].click();
    app.tick();
    expect(types()).toEqual(['password', 'password', 'password']);
  });

  it('follows the app-wide state that every eye button shares', () => {
    const { el } = setup();
    // The root service is the same instance the login and register pages use,
    // so revealing PINs there reveals them here.
    TestBed.inject(PinVisibility).visible.set(true);
    TestBed.inject(ApplicationRef).tick();

    const types = [
      ...el.querySelectorAll<HTMLInputElement>('#currentPin, #newPin, #confirmPin'),
    ].map((input) => input.type);
    expect(types).toEqual(['text', 'text', 'text']);
  });
});

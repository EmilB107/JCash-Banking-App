import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { AuthService } from '../../../core/auth/auth.service';
import { User } from '../../../core/auth/user';
import { Register } from './register';

describe('Register', () => {
  const register = vi.fn();

  function setup() {
    TestBed.configureTestingModule({
      imports: [Register],
      providers: [provideRouter([]), { provide: AuthService, useValue: { register } }],
    });
    const fixture = TestBed.createComponent(Register);
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;
    const type = (id: string, value: string) => {
      const input = el.querySelector<HTMLInputElement>(`#${id}`)!;
      input.value = value;
      input.dispatchEvent(new Event('input'));
    };
    const fillValid = () => {
      type('displayName', ' Demo User ');
      type('email', 'demo@example.com');
      type('mobileNumber', '09171234567');
      type('pin', '123456');
      type('confirmPin', '123456');
    };
    const submit = () => {
      el.querySelector('form')!.dispatchEvent(new Event('submit'));
      fixture.detectChanges();
    };
    return { el, type, fillValid, submit };
  }

  beforeEach(() => register.mockReset());

  it('rejects a bad mobile number and mismatched PINs without calling the API', () => {
    const { el, type, fillValid, submit } = setup();
    fillValid();
    type('mobileNumber', '12345');
    type('confirmPin', '654321');
    submit();

    expect(register).not.toHaveBeenCalled();
    expect(el.textContent).toContain('Use 0917 123 4567 or +63 917 123 4567.');
    expect(el.textContent).toContain("PINs don't match.");
  });

  it('shows the mobile number with spaces but sends it without', () => {
    register.mockReturnValue(of({} as User));
    const { el, type, fillValid, submit } = setup();
    vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    fillValid();
    type('mobileNumber', '+639171234567');

    expect(el.querySelector<HTMLInputElement>('#mobileNumber')!.value).toBe('+63 917 123 4567');
    submit();
    expect(register).toHaveBeenCalledWith(
      expect.objectContaining({ mobileNumber: '+639171234567' }),
    );
  });

  it('rejects an email without a dot in the domain', () => {
    const { el, type, fillValid, submit } = setup();
    fillValid();
    type('email', 'demo@example');
    submit();

    expect(register).not.toHaveBeenCalled();
    expect(el.textContent).toContain('Enter a valid email address.');
  });

  it('registers with trimmed values and goes to the dashboard', () => {
    register.mockReturnValue(of({} as User));
    const { fillValid, submit } = setup();
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    fillValid();
    submit();

    expect(register).toHaveBeenCalledWith({
      email: 'demo@example.com',
      mobileNumber: '09171234567',
      displayName: 'Demo User',
      pin: '123456',
    });
    expect(navigate).toHaveBeenCalledWith('/dashboard');
  });

  it('shows "already registered" under the matching field', () => {
    register.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 409,
            error: { error: 'MOBILE_NUMBER_ALREADY_REGISTERED', message: 'Mobile number taken' },
          }),
      ),
    );
    const { el, fillValid, submit } = setup();
    fillValid();
    submit();

    const mobile = el.querySelector('#mobileNumber')!;
    expect(mobile.getAttribute('aria-invalid')).toBe('true');
    expect(mobile.parentElement!.textContent).toContain('Mobile number taken');
    expect(el.querySelector('[role=alert]')).toBeNull();
  });
});

import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { AuthService } from '../../../core/auth/auth.service';
import { User } from '../../../core/auth/user';
import { Login } from './login';

describe('Login', () => {
  const login = vi.fn();

  function setup() {
    TestBed.configureTestingModule({
      imports: [Login],
      providers: [provideRouter([]), { provide: AuthService, useValue: { login } }],
    });
    const fixture = TestBed.createComponent(Login);
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;
    const type = (id: string, value: string) => {
      const input = el.querySelector<HTMLInputElement>(`#${id}`)!;
      input.value = value;
      input.dispatchEvent(new Event('input'));
    };
    const submit = () => {
      el.querySelector('form')!.dispatchEvent(new Event('submit'));
      fixture.detectChanges();
    };
    return { el, type, submit };
  }

  beforeEach(() => login.mockReset());

  it('does not call the API when the form is invalid', () => {
    const { el, type, submit } = setup();
    type('pin', '12');
    submit();

    expect(login).not.toHaveBeenCalled();
    expect(el.textContent).toContain('Enter your email or mobile number.');
    expect(el.textContent).toContain('Enter your 6-digit PIN.');
  });

  it('logs in and goes to the dashboard', () => {
    login.mockReturnValue(of({} as User));
    const { type, submit } = setup();
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);

    type('identifier', '  demo@example.com ');
    type('pin', '123456');
    submit();

    expect(login).toHaveBeenCalledWith('demo@example.com', '123456');
    expect(navigate).toHaveBeenCalledWith('/dashboard');
  });

  it("shows the backend's message and clears the PIN on failure", () => {
    login.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 423,
            error: { error: 'ACCOUNT_LOCKED', message: 'Account is locked' },
          }),
      ),
    );
    const { el, type, submit } = setup();

    type('identifier', 'demo@example.com');
    type('pin', '000000');
    submit();

    expect(el.querySelector('[role=alert]')?.textContent).toContain('Account is locked');
    expect(el.querySelector<HTMLInputElement>('#pin')!.value).toBe('');
  });
});

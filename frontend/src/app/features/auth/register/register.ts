import { Component, inject, signal } from '@angular/core';
import {
  AbstractControl,
  NonNullableFormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { AuthService } from '../../../core/auth/auth.service';
import { toApiError } from '../../../core/http/api-error';
import { AuthLayout } from '../auth-layout';

// Same rules as the backend's CreateUserRequest, so most mistakes are caught
// before a request is sent. The backend still validates everything.
const MOBILE_PATTERN = /^(09\d{9}|\+639\d{9})$/;
const PIN_PATTERN = /^\d{6}$/;
// Validators.email alone accepts "a@b"; this also requires a dot in the domain.
const EMAIL_PATTERN = /^[^@\s]+@[^@\s]+\.[^@\s]+$/;

type Field = 'email' | 'mobileNumber' | 'displayName' | 'pin' | 'confirmPin';

function pinsMatch(group: AbstractControl): ValidationErrors | null {
  return group.get('pin')?.value === group.get('confirmPin')?.value ? null : { pinMismatch: true };
}

@Component({
  selector: 'app-register',
  imports: [ReactiveFormsModule, RouterLink, AuthLayout],
  templateUrl: './register.html',
})
export class Register {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly form = inject(NonNullableFormBuilder).group(
    {
      email: ['', [Validators.required, Validators.email, Validators.pattern(EMAIL_PATTERN)]],
      mobileNumber: ['', [Validators.required, Validators.pattern(MOBILE_PATTERN)]],
      displayName: ['', [Validators.required, Validators.maxLength(100)]],
      pin: ['', [Validators.required, Validators.pattern(PIN_PATTERN)]],
      confirmPin: ['', Validators.required],
    },
    { validators: pinsMatch },
  );
  protected readonly submitting = signal(false);
  protected readonly error = signal<string | null>(null);

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.error.set(null);

    const { email, mobileNumber, displayName, pin } = this.form.getRawValue();
    this.auth
      .register({
        email: email.trim(),
        mobileNumber: mobileNumber.trim(),
        displayName: displayName.trim(),
        pin,
      })
      .pipe(finalize(() => this.submitting.set(false)))
      .subscribe({
        next: () => void this.router.navigateByUrl('/dashboard'),
        error: (err: unknown) => this.showServerError(toApiError(err)),
      });
  }

  protected errorFor(name: Field): string | null {
    const control = this.form.controls[name];
    if (!control.touched) {
      return null;
    }
    const errors = control.errors;
    if (errors?.['server']) {
      return errors['server'];
    }
    if (name === 'confirmPin' && this.form.hasError('pinMismatch')) {
      return "PINs don't match.";
    }
    if (!errors) {
      return null;
    }
    switch (name) {
      case 'email':
        return errors['required'] ? 'Enter your email.' : 'Enter a valid email address.';
      case 'mobileNumber':
        return 'Use 09XXXXXXXXX or +639XXXXXXXXX.';
      case 'displayName':
        return errors['required'] ? 'Enter your name.' : 'Use at most 100 characters.';
      case 'pin':
        return 'PIN must be exactly 6 digits.';
      case 'confirmPin':
        return 'Re-enter your PIN.';
    }
  }

  /**
   * "Already registered" errors belong to a specific field, so show them there.
   * Setting a `server` error on the control also makes it invalid until the
   * user edits it (editing re-runs the validators, which clears it).
   */
  private showServerError(error: { error: string; message: string }): void {
    const field =
      error.error === 'EMAIL_ALREADY_REGISTERED'
        ? this.form.controls.email
        : error.error === 'MOBILE_NUMBER_ALREADY_REGISTERED'
          ? this.form.controls.mobileNumber
          : null;

    if (field) {
      field.setErrors({ server: error.message });
      field.markAsTouched();
    } else {
      this.error.set(error.message);
    }
  }
}

import { Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { AuthService } from '../../../core/auth/auth.service';
import { MobileFormatDirective } from '../../../core/forms/mobile-format.directive';
import { looksLikeMobile, stripSpaces } from '../../../core/forms/mobile-number';
import { toApiError } from '../../../core/http/api-error';
import { PinField } from '../../../core/forms/pin-field';
import { AuthLayout } from '../auth-layout';

@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule, RouterLink, AuthLayout, MobileFormatDirective, PinField],
  templateUrl: './login.html',
})
export class Login {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly form = inject(NonNullableFormBuilder).group({
    identifier: ['', [Validators.required, Validators.maxLength(255)]],
    pin: ['', [Validators.required, Validators.pattern(/^\d{6}$/)]],
  });
  protected readonly submitting = signal(false);
  protected readonly error = signal<string | null>(null);

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.error.set(null);

    const { identifier, pin } = this.form.getRawValue();
    // A mobile number is shown as "0917 123 4567"; the backend expects no spaces.
    const id = looksLikeMobile(identifier) ? stripSpaces(identifier) : identifier.trim();
    this.auth
      .login(id, pin)
      .pipe(finalize(() => this.submitting.set(false)))
      .subscribe({
        next: () => void this.router.navigateByUrl('/dashboard'),
        // INVALID_CREDENTIALS (401) and ACCOUNT_LOCKED (423) both come with a
        // message meant for the user, so show it as-is.
        error: (err: unknown) => {
          this.error.set(toApiError(err).message);
          this.form.controls.pin.reset();
        },
      });
  }

  /** Show a field's error only after the user has left it (or tried to submit). */
  protected invalid(name: 'identifier' | 'pin'): boolean {
    const control = this.form.controls[name];
    return control.invalid && control.touched;
  }
}

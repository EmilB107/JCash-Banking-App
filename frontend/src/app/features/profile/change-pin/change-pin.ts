import { Component, ElementRef, afterNextRender, inject, signal, viewChild } from '@angular/core';
import {
  AbstractControl,
  NonNullableFormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { LucideArrowLeft } from '@lucide/angular';
import { finalize } from 'rxjs';
import { AuthService } from '../../../core/auth/auth.service';
import { PinField } from '../../../core/forms/pin-field';
import { PIN_PATTERN, pinsMatch } from '../../../core/forms/validators';
import { toApiError } from '../../../core/http/api-error';

type Field = 'currentPin' | 'newPin' | 'confirmPin';

/** Navigation state the profile page reads to show "Your PIN was changed." */
export interface PinChangedState {
  pinChanged: true;
}

// Reusing the current PIN would make "change PIN" a no-op, so catch it early.
function differentFromCurrent(group: AbstractControl): ValidationErrors | null {
  const newPin = group.get('newPin')?.value;
  return newPin && newPin === group.get('currentPin')?.value ? { samePin: true } : null;
}

@Component({
  selector: 'app-change-pin',
  imports: [ReactiveFormsModule, RouterLink, LucideArrowLeft, PinField],
  templateUrl: './change-pin.html',
})
export class ChangePin {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly currentPinInput =
    viewChild.required<ElementRef<HTMLInputElement>>('currentPinInput');

  protected readonly submitting = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly form = inject(NonNullableFormBuilder).group(
    {
      currentPin: ['', [Validators.required, Validators.pattern(PIN_PATTERN)]],
      newPin: ['', [Validators.required, Validators.pattern(PIN_PATTERN)]],
      confirmPin: ['', Validators.required],
    },
    { validators: [pinsMatch('newPin', 'confirmPin'), differentFromCurrent] },
  );

  constructor() {
    // The page has one job, so start the cursor in its first field.
    afterNextRender(() => this.currentPinInput().nativeElement.focus());
  }

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.error.set(null);

    const { currentPin, newPin } = this.form.getRawValue();
    this.auth
      .changePin(currentPin, newPin)
      .pipe(finalize(() => this.submitting.set(false)))
      .subscribe({
        next: () => {
          const state: PinChangedState = { pinChanged: true };
          void this.router.navigateByUrl('/profile', { state });
        },
        error: (err: unknown) => {
          const error = toApiError(err);
          if (error.error === 'INCORRECT_PIN') {
            const field = this.form.controls.currentPin;
            field.setErrors({ server: error.message });
            field.markAsTouched();
          } else {
            this.error.set(error.message);
          }
        },
      });
  }

  protected errorFor(name: Field): string | null {
    const control = this.form.controls[name];
    if (!control.touched) {
      return null;
    }
    if (control.errors?.['server']) {
      return control.errors['server'];
    }
    if (name === 'newPin' && !control.errors && this.form.hasError('samePin')) {
      return 'Choose a PIN different from your current one.';
    }
    if (name === 'confirmPin' && !control.errors && this.form.hasError('pinMismatch')) {
      return "PINs don't match.";
    }
    if (!control.errors) {
      return null;
    }
    return name === 'confirmPin' ? 'Re-enter your new PIN.' : 'PIN must be exactly 6 digits.';
  }
}

import { DatePipe } from '@angular/common';
import {
  Component,
  ElementRef,
  Injector,
  Signal,
  afterNextRender,
  computed,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { toSignal } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { MobileFormatDirective } from '../../core/forms/mobile-format.directive';
import { formatMobile, stripSpaces, toLocalMobile } from '../../core/forms/mobile-number';
import { EMAIL_PATTERN, mobileNumber } from '../../core/forms/validators';
import { ApiError, toApiError } from '../../core/http/api-error';
import { PinChangedState } from './change-pin/change-pin';

type ProfileField = 'displayName' | 'email' | 'mobileNumber';

@Component({
  selector: 'app-profile',
  imports: [DatePipe, ReactiveFormsModule, RouterLink, MobileFormatDirective],
  templateUrl: './profile.html',
})
export class Profile {
  private readonly auth = inject(AuthService);
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly injector = inject(Injector);

  private readonly editButton = viewChild<ElementRef<HTMLElement>>('editButton');
  private readonly nameInput = viewChild<ElementRef<HTMLElement>>('nameInput');

  protected readonly user = this.auth.currentUser;
  protected readonly formatMobile = formatMobile;

  // Set when the change-PIN page sends the user back here after a successful
  // change. Read while this page is being navigated to, so it's only shown once.
  protected readonly pinChanged = !!(
    inject(Router).currentNavigation()?.extras.state as PinChangedState | undefined
  )?.pinChanged;

  protected readonly editing = signal(false);
  protected readonly saving = signal(false);
  protected readonly profileError = signal<string | null>(null);
  protected readonly profileSaved = signal(false);
  protected readonly profileForm = this.fb.group({
    displayName: ['', [Validators.required, Validators.maxLength(100)]],
    email: ['', [Validators.required, Validators.email, Validators.pattern(EMAIL_PATTERN)]],
    mobileNumber: ['', [Validators.required, mobileNumber]],
  });

  // The form's values as a signal, so `hasChanges` updates as the user types.
  private readonly formValue = toSignal(this.profileForm.valueChanges, {
    initialValue: this.profileForm.getRawValue(),
  });

  /**
   * Save is only enabled when something would actually change. Values are
   * compared the way the backend stores them, so "Juan@Example.com" or
   * "+63 917 123 4567" count as unchanged, and so does undoing an edit.
   */
  protected readonly hasChanges = computed(() => {
    const user = this.user();
    const { displayName = '', email = '', mobileNumber = '' } = this.formValue();
    return (
      !!user &&
      (displayName.trim() !== user.displayName ||
        email.trim().toLowerCase() !== user.email ||
        toLocalMobile(mobileNumber) !== user.mobileNumber)
    );
  });

  protected startEditing(): void {
    const user = this.user();
    if (!user) {
      return;
    }
    this.profileForm.reset({
      displayName: user.displayName,
      email: user.email,
      mobileNumber: formatMobile(user.mobileNumber),
    });
    this.profileError.set(null);
    this.profileSaved.set(false);
    this.editing.set(true);
    this.focusAfterRender(this.nameInput);
  }

  protected cancelEditing(): void {
    this.editing.set(false);
    this.focusAfterRender(this.editButton);
  }

  protected saveProfile(): void {
    if (!this.hasChanges()) {
      return;
    }
    if (this.profileForm.invalid) {
      this.profileForm.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    this.profileError.set(null);

    const { displayName, email, mobileNumber } = this.profileForm.getRawValue();
    this.auth
      .updateProfile({
        displayName: displayName.trim(),
        email: email.trim(),
        mobileNumber: stripSpaces(mobileNumber),
      })
      .pipe(finalize(() => this.saving.set(false)))
      .subscribe({
        next: () => {
          this.editing.set(false);
          this.profileSaved.set(true);
          this.focusAfterRender(this.editButton);
        },
        error: (err: unknown) => this.showProfileServerError(toApiError(err)),
      });
  }

  protected profileErrorFor(name: ProfileField): string | null {
    const control = this.profileForm.controls[name];
    const errors = control.touched ? control.errors : null;
    if (!errors) {
      return null;
    }
    if (errors['server']) {
      return errors['server'];
    }
    switch (name) {
      case 'displayName':
        return errors['required'] ? 'Enter your name.' : 'Use at most 100 characters.';
      case 'email':
        return errors['required'] ? 'Enter your email.' : 'Enter a valid email address.';
      case 'mobileNumber':
        return 'Use 0917 123 4567 or +63 917 123 4567.';
    }
  }

  /**
   * The button that was clicked disappears when the form opens or closes, which
   * would drop keyboard and screen-reader users back at the top of the page.
   * Move focus to the new element once Angular has rendered it.
   */
  private focusAfterRender(target: Signal<ElementRef<HTMLElement> | undefined>): void {
    afterNextRender(() => target()?.nativeElement.focus(), { injector: this.injector });
  }

  private showProfileServerError(error: ApiError): void {
    const field =
      error.error === 'EMAIL_ALREADY_REGISTERED'
        ? this.profileForm.controls.email
        : error.error === 'MOBILE_NUMBER_ALREADY_REGISTERED'
          ? this.profileForm.controls.mobileNumber
          : null;

    if (field) {
      field.setErrors({ server: error.message });
      field.markAsTouched();
    } else {
      this.profileError.set(error.message);
    }
  }
}

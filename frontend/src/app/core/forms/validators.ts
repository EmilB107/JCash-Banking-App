import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';
import { MOBILE_PATTERN, stripSpaces } from './mobile-number';

// Same rules as the backend's request DTOs, so most mistakes are caught before
// a request is sent. The backend still validates everything.
export const PIN_PATTERN = /^\d{6}$/;
// Validators.email alone accepts "a@b"; this also requires a dot in the domain.
export const EMAIL_PATTERN = /^[^@\s]+@[^@\s]+\.[^@\s]+$/;

/** The field shows "0917 123 4567", so check the number with the spaces removed. */
export function mobileNumber(control: AbstractControl<string>): ValidationErrors | null {
  return !control.value || MOBILE_PATTERN.test(stripSpaces(control.value))
    ? null
    : { mobileNumber: true };
}

/** Form-group validator: `{ pinMismatch: true }` when the two fields differ. */
export function pinsMatch(pinField: string, confirmField: string): ValidatorFn {
  return (group) =>
    group.get(pinField)?.value === group.get(confirmField)?.value ? null : { pinMismatch: true };
}

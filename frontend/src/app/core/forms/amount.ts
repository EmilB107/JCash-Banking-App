import { ValidationErrors, ValidatorFn, Validators } from '@angular/forms';

/** Whole pesos or up to 2 decimal places (centavos), matching the backend's NUMERIC(19, 2). */
const AMOUNT_PATTERN = /^\d+(\.\d{1,2})?$/;

export function amountValidators(max?: number): ValidatorFn[] {
  const validators = [
    Validators.required,
    Validators.pattern(AMOUNT_PATTERN),
    Validators.min(0.01),
  ];
  return max === undefined ? validators : [...validators, Validators.max(max)];
}

export function amountErrorMessage(errors: ValidationErrors): string {
  if (errors['required']) {
    return 'Enter an amount.';
  }
  if (errors['pattern']) {
    return 'Use numbers only, with up to 2 decimal places.';
  }
  if (errors['min']) {
    return 'Enter at least ₱0.01.';
  }
  if (errors['max']) {
    return `The most you can enter is ₱${errors['max'].max.toLocaleString('en-US')}.`;
  }
  return 'Enter a valid amount.';
}

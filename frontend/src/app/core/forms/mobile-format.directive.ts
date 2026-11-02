import { Directive, ElementRef, HostListener, inject } from '@angular/core';
import { NgControl } from '@angular/forms';
import { formatMobile, looksLikeMobile, maxMobileLength, stripSpaces } from './mobile-number';

/**
 * Formats a mobile number with spaces while the user types
 * ("09171234567" -> "0917 123 4567"). Input that doesn't look like a mobile
 * number (e.g. an email) is left untouched, so this also works on the
 * "Email or mobile number" login field.
 *
 * Usage: <input type="tel" formControlName="mobileNumber" appMobileFormat />
 * Use type="tel" or "text": browsers don't allow caret control on type="email".
 * The form control holds the spaced value (see stripSpaces).
 */
@Directive({ selector: 'input[appMobileFormat]' })
export class MobileFormatDirective {
  private readonly input: HTMLInputElement = inject(ElementRef).nativeElement;
  private readonly control = inject(NgControl, { self: true });

  /**
   * Backspace/Delete next to a space would only remove the space, which the
   * formatter immediately puts back, so the key would seem to do nothing.
   * Instead, delete the digit on the other side of the space.
   */
  @HostListener('beforeinput', ['$event'])
  onBeforeInput(event: InputEvent): void {
    const { value, selectionStart: start, selectionEnd: end } = this.input;
    if (start === null || start !== end) {
      return; // A selected range is replaced or deleted normally.
    }
    // Once the number is full, ignore more digits; otherwise typing in the
    // middle would push the last digit off the end.
    if (event.inputType === 'insertText' && looksLikeMobile(value)) {
      const compact = stripSpaces(value);
      if (compact.length >= maxMobileLength(compact) && /^\d+$/.test(event.data ?? '')) {
        event.preventDefault();
        return;
      }
    }
    if (event.inputType === 'deleteContentBackward' && value[start - 1] === ' ' && start >= 2) {
      event.preventDefault();
      this.apply(value.slice(0, start - 2) + value.slice(start), start - 2);
    } else if (event.inputType === 'deleteContentForward' && value[start] === ' ') {
      event.preventDefault();
      this.apply(value.slice(0, start + 1) + value.slice(start + 2), start);
    }
  }

  @HostListener('input')
  onInput(): void {
    this.apply(this.input.value, this.input.selectionStart ?? this.input.value.length);
  }

  /** Writes the formatted value, keeping the caret after the same number of non-space characters. */
  private apply(raw: string, caret: number): void {
    const formatted = formatMobile(raw);
    const charsBeforeCaret = raw.slice(0, caret).replace(/\s/g, '').length;

    const position = caretAfter(formatted, charsBeforeCaret);
    this.input.value = formatted;
    this.input.setSelectionRange(position, position);
    // Keep the form control in sync (the view already shows `formatted`).
    this.control.control?.setValue(formatted, { emitModelToViewChange: false });
  }
}

/** Index in `text` just after its `count`-th non-space character. */
function caretAfter(text: string, count: number): number {
  if (count === 0) {
    return 0;
  }
  let seen = 0;
  for (let i = 0; i < text.length; i++) {
    if (text[i] !== ' ' && ++seen === count) {
      return i + 1;
    }
  }
  return text.length;
}

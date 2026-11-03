import {
  Component,
  ElementRef,
  Injectable,
  contentChild,
  effect,
  inject,
  input,
  signal,
} from '@angular/core';
import { NgControl } from '@angular/forms';
import { LucideEye, LucideEyeOff } from '@lucide/angular';

/**
 * Whether PINs are shown. One instance for the whole app (providedIn: 'root'),
 * so every eye button on every page shares the same state: reveal PINs on one
 * page and they're revealed everywhere. It lives in memory only, so a reload
 * starts hidden again.
 */
@Injectable({ providedIn: 'root' })
export class PinVisibility {
  readonly visible = signal(false);
}

/**
 * Usage (the input needs a form control, and pr-10 to clear the icon):
 *
 *   <app-pin-field label="PINs">
 *     <input class="form-input pr-10" type="password" formControlName="pin" ... />
 *   </app-pin-field>
 */
@Component({
  selector: 'app-pin-field',
  imports: [LucideEye, LucideEyeOff],
  host: { class: 'relative block' },
  template: `
    <ng-content />
    @if (hasValue()) {
      <!-- top-1 skips the input's mt-1 margin so the icon is centered on the input. -->
      <div class="absolute top-1 right-0 bottom-0 flex items-center pr-2">
        <button
          class="rounded-md p-1 text-ink-500 hover:text-ink-800 focus-visible:outline-2 focus-visible:outline-brand-500"
          type="button"
          [attr.aria-label]="(visibility.visible() ? 'Hide ' : 'Show ') + label()"
          (click)="visibility.visible.set(!visibility.visible())"
        >
          @if (visibility.visible()) {
            <svg lucideEyeOff size="18"></svg>
          } @else {
            <svg lucideEye size="18"></svg>
          }
        </button>
      </div>
    }
  `,
})
export class PinField {
  readonly label = input('PIN');
  protected readonly visibility = inject(PinVisibility);
  protected readonly hasValue = signal(false);

  // The projected input: its form control (for the value) and the element (for its type).
  private readonly control = contentChild.required(NgControl);
  private readonly inputElement = contentChild.required(NgControl, { read: ElementRef });

  constructor() {
    effect(() => {
      const input: HTMLInputElement = this.inputElement().nativeElement;
      input.type = this.visibility.visible() ? 'text' : 'password';
    });

    // valueChanges also fires when code changes the value (e.g. reset() after a
    // failed login), which the DOM's "input" event would miss.
    effect((onCleanup) => {
      const control = this.control();
      this.hasValue.set(!!control.value);
      const subscription = control.valueChanges?.subscribe((value) => this.hasValue.set(!!value));
      onCleanup(() => subscription?.unsubscribe());
    });
  }
}

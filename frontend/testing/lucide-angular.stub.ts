/**
 * Test-only stand-in for @lucide/angular (wired up in vitest-base.config.mts).
 *
 * The real package defines ~1,600 icon components that Angular compiles at
 * import time under the test runner, adding ~15 s to every spec that touches
 * an icon. These empty <svg> components accept the same inputs, so templates
 * render the same way without drawing anything.
 *
 * Using a new icon in the app? Export a stub for it here too, or the specs
 * that render it fail with "is not a known element/property".
 */
import { Component } from '@angular/core';

export type LucideIcon = unknown;

const inputs = ['size', 'strokeWidth', 'color', 'title'];

// Vitest doesn't compile decorator syntax in this file, so `@Component({...})`
// is written as a plain call: Component({...})(class {}) does the same thing.
function icon(selector: string, extraInputs: string[] = []) {
  return Component({ selector, template: '', inputs: [...inputs, ...extraInputs] })(class {});
}

export const LucideDynamicIcon = icon('svg[lucideIcon]', ['lucideIcon']);
export const LucideEye = icon('svg[lucideEye]');
export const LucideEyeOff = icon('svg[lucideEyeOff]');
export const LucideHouse = icon('svg[lucideHouse]');
export const LucideCirclePlus = icon('svg[lucideCirclePlus]');
export const LucideSend = icon('svg[lucideSend]');
export const LucideClock = icon('svg[lucideClock]');
export const LucideUser = icon('svg[lucideUser]');
export const LucideMenu = icon('svg[lucideMenu]');
export const LucideX = icon('svg[lucideX]');

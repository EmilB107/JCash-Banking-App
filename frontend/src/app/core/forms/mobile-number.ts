/**
 * Display helpers for Philippine mobile numbers. The spaces are for
 * readability only; call `stripSpaces` before validating or sending.
 */

/** Same rule as the backend's CreateUserRequest (spaces must be removed first). */
export const MOBILE_PATTERN = /^(09\d{9}|\+639\d{9})$/;

/**
 * True when the input could be a mobile number: an optional leading "+"
 * followed only by digits and spaces. Anything with a letter or "@" is
 * treated as an email, so it is left exactly as typed.
 */
export function looksLikeMobile(value: string): boolean {
  return /^\+?[\d\s]+$/.test(value.trim());
}

export function stripSpaces(value: string): string {
  return value.replace(/\s/g, '');
}

/**
 * The form the backend stores (see MobileNumberNormalizer):
 * "+63 917 123 4567" -> "09171234567". Other shapes only lose their spaces.
 */
export function toLocalMobile(value: string): string {
  const compact = stripSpaces(value);
  return /^\+639\d{9}$/.test(compact) ? '0' + compact.slice(3) : compact;
}

/**
 * Longest allowed length without spaces: 11 for "09XXXXXXXXX", 13 for
 * "+639XXXXXXXXX". Other shapes have no limit (they fail validation anyway).
 */
export function maxMobileLength(compact: string): number {
  if (compact.startsWith('+63')) {
    return 13;
  }
  return compact.startsWith('0') ? 11 : Infinity;
}

/**
 * Groups the digits for display, dropping digits past the allowed length:
 *   09171234567   -> 0917 123 4567
 *   +639171234567 -> +63 917 123 4567
 */
export function formatMobile(value: string): string {
  if (!looksLikeMobile(value)) {
    return value;
  }
  let compact = stripSpaces(value);
  compact = compact.slice(0, maxMobileLength(compact));

  if (compact.startsWith('+63')) {
    return ['+63', ...group(compact.slice(3), [3, 3])].join(' ');
  }
  if (compact.startsWith('0')) {
    return group(compact, [4, 3]).join(' ');
  }
  // Some other shape (e.g. "917..." or "+1..."): don't guess a grouping.
  return compact;
}

/** Splits `digits` into chunks of the given sizes; the rest becomes the last chunk. */
function group(digits: string, sizes: number[]): string[] {
  const parts: string[] = [];
  let start = 0;
  for (const size of sizes) {
    if (start >= digits.length) {
      break;
    }
    parts.push(digits.slice(start, start + size));
    start += size;
  }
  if (start < digits.length) {
    parts.push(digits.slice(start));
  }
  return parts;
}

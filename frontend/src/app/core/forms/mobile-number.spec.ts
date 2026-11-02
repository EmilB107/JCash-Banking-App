import { formatMobile, looksLikeMobile, stripSpaces } from './mobile-number';

describe('mobile number helpers', () => {
  it('tells mobile numbers apart from emails', () => {
    expect(looksLikeMobile('0917 123 4567')).toBe(true);
    expect(looksLikeMobile('+639171234567')).toBe(true);
    expect(looksLikeMobile('demo@example.com')).toBe(false);
    expect(looksLikeMobile('09demo')).toBe(false);
    expect(looksLikeMobile('')).toBe(false);
  });

  it('groups local and international numbers as they are typed', () => {
    expect(formatMobile('0')).toBe('0');
    expect(formatMobile('09171')).toBe('0917 1');
    expect(formatMobile('09171234567')).toBe('0917 123 4567');
    expect(formatMobile('+63')).toBe('+63');
    expect(formatMobile('+639171')).toBe('+63 917 1');
    expect(formatMobile('+639171234567')).toBe('+63 917 123 4567');
  });

  it('regroups messy spacing and drops digits past the allowed length', () => {
    expect(formatMobile('09 17123 4567')).toBe('0917 123 4567');
    expect(formatMobile('091712345678')).toBe('0917 123 4567');
    expect(formatMobile('+6391712345678')).toBe('+63 917 123 4567');
  });

  it('leaves emails and unknown shapes alone', () => {
    expect(formatMobile('demo@example.com')).toBe('demo@example.com');
    expect(formatMobile('9171234567')).toBe('9171234567');
  });

  it('strips spaces for sending', () => {
    expect(stripSpaces('+63 917 123 4567')).toBe('+639171234567');
  });
});

import { HttpErrorResponse } from '@angular/common/http';
import { toApiError } from './api-error';

describe('toApiError', () => {
  it('returns the backend error body', () => {
    const err = new HttpErrorResponse({
      status: 409,
      error: { error: 'EMAIL_ALREADY_REGISTERED', message: 'Email is already registered' },
    });
    expect(toApiError(err)).toEqual({
      error: 'EMAIL_ALREADY_REGISTERED',
      message: 'Email is already registered',
    });
  });

  it('reports network failures', () => {
    expect(toApiError(new HttpErrorResponse({ status: 0 })).error).toBe('NETWORK_ERROR');
  });

  it('falls back for unexpected bodies', () => {
    expect(toApiError(new HttpErrorResponse({ status: 502, error: '<html>' })).error).toBe(
      'UNEXPECTED_ERROR',
    );
    expect(toApiError(new Error('boom')).error).toBe('UNEXPECTED_ERROR');
  });
});

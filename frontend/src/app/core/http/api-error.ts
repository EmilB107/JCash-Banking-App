import { HttpErrorResponse } from '@angular/common/http';

/** Error body returned by the backend (com.jcash.app.common.ApiError). */
export interface ApiError {
  error: string;
  message: string;
}

/**
 * Converts any HTTP failure into an ApiError so views can show `message`
 * and branch on `error` (e.g. INVALID_CREDENTIALS, ACCOUNT_LOCKED).
 */
export function toApiError(err: unknown): ApiError {
  if (err instanceof HttpErrorResponse) {
    if (err.status === 0) {
      return {
        error: 'NETWORK_ERROR',
        message: 'Cannot reach the server. Check your connection and try again.',
      };
    }
    if (isApiError(err.error)) {
      return err.error;
    }
  }
  return { error: 'UNEXPECTED_ERROR', message: 'Something went wrong. Please try again.' };
}

function isApiError(body: unknown): body is ApiError {
  return (
    typeof body === 'object' &&
    body !== null &&
    typeof (body as ApiError).error === 'string' &&
    typeof (body as ApiError).message === 'string'
  );
}

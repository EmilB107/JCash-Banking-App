# Security

Security is a core requirement even though JCash simulates money.

## Authentication and authorization
- Hash credentials with a well-supported algorithm such as BCrypt or Argon2; never store plaintext credentials. JCash uses a 6-digit PIN hashed with BCrypt through Spring Security's `PasswordEncoder`.
- Limit guessing: after 3 consecutive wrong PINs the account is locked for 5 minutes (HTTP 423 `ACCOUNT_LOCKED`), even for the correct PIN, and unlocks automatically. A successful login resets the count. Wrong current PINs on the change-PIN endpoint share this counter, so an unattended session cannot be used to guess the PIN without limit. Login attempts lock the user row (`SELECT ... FOR UPDATE`), so concurrent guesses cannot exceed the limit. Unknown identifiers return the same HTTP 401 as a wrong PIN and lock nothing; a locked response does reveal that the identifier exists.
- Use Spring Security to protect endpoints. JCash currently uses server-side HTTP sessions, CSRF protection, and a database-backed `UserDetailsService`.
- Keep registration, login, CSRF-token retrieval, and the API documentation (`/v3/api-docs`, Swagger UI) public; require authentication for protected endpoints and invalidate the session on logout.
- CSRF tokens are stored in a JavaScript-readable `XSRF-TOKEN` cookie and must be sent back in the `X-XSRF-TOKEN` header. `LenientCsrfTokenRequestHandler` accepts the masked token from `GET /api/auth/csrf` or the raw cookie value; Spring's `spa()` handler was not used because it rejects the masked token in a header.
- Check ownership on every account, transfer, and transaction-history request; a user must not access another user's records by changing an identifier.
- Choose session-based authentication or JWT deliberately based on the application's needs. JWT is not mandatory.
- Role-based access control is optional unless an administrator role is introduced.

## Input and API safety
- Validate request data, positive amounts, currencies, recipient existence, and business rules on the server.
- Return appropriate HTTP status codes and consistent errors without leaking secrets, stack traces, or sensitive implementation details.
- Apply transaction integrity, concurrency protection, and idempotency to transfer endpoints.
- Follow relevant OWASP guidance and test authorization failures as well as successful flows.

## Secrets and deployment
- Keep passwords, tokens, and connection strings out of source control; use environment variables or the hosting provider's secret manager.
- Use HTTPS/TLS for public traffic. The deployed frontend forwards `/api` to the backend, so the browser sees one origin and no CORS configuration is needed; if the API is ever called cross-origin, allow only the intended frontend origin.
- Never implement custom cryptographic algorithms.
- Do not log PINs, passwords, tokens, or unnecessary personal/financial details.
- Clearly state that the app is a simulation and does not process real money.

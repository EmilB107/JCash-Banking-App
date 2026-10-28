# Security

Security is a core requirement even though JCash simulates money.

## Authentication and authorization
- Hash passwords with a well-supported algorithm such as BCrypt or Argon2; never store plaintext passwords. JCash currently uses BCrypt through Spring Security's `PasswordEncoder`.
- Use Spring Security to protect endpoints. JCash currently uses server-side HTTP sessions, CSRF protection, and a database-backed `UserDetailsService`.
- Keep registration, login, and CSRF-token retrieval public; require authentication for protected endpoints and invalidate the session on logout.
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
- Use HTTPS/TLS for public traffic and configure CORS for the intended frontend origin.
- Never implement custom cryptographic algorithms.
- Do not log passwords, tokens, or unnecessary personal/financial details.
- Clearly state that the app is a simulation and does not process real money.

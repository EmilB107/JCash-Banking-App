# Testing Strategy

Automated tests should be developed alongside features, not postponed until the end.

## Core tools
- **JUnit 5:** unit tests
- **Mockito:** isolate services and simulate dependency behavior
- **Spring Boot Test:** application-level tests
- **MockMvc:** later/API integration tests for HTTP behavior
- **Testcontainers:** optional integration tests against real PostgreSQL
- **Angular testing tools:** component, service, form, and UI tests
- **Playwright:** optional end-to-end browser workflows

## Essential transfer scenarios
- Valid transfer updates sender and recipient correctly.
- Insufficient funds and invalid/nonexistent recipients are rejected.
- Invalid amounts and malformed requests are rejected.
- Any failed write rolls back all transfer and ledger changes.
- Concurrent transfers cannot overspend the same account.
- Repeating a request with the same idempotency key does not create a second transfer.
- A user cannot access another user's accounts or transaction history.
- Ledger debit/credit totals balance and maintained balances reconcile.

Use deterministic test data. Integration tests should exercise transaction behavior and database constraints, not only mocked service logic. Never use real financial or personal data in test fixtures.

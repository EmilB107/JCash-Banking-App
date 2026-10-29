# Testing Strategy

Automated tests should be developed alongside features, not postponed until the end.

## Core tools
- **JUnit 5:** unit tests
- **Mockito:** isolate services and simulate dependency failures
- **Spring Boot Test:** application-level tests
- **MockMvc:** API and authentication/header behavior tests
- **Testcontainers:** optional integration tests against real PostgreSQL
- **Angular testing tools:** component, service, form, and UI tests
- **Playwright:** optional end-to-end browser workflows

## Transfer scenarios
The transfer unit tests (`TransferServiceTest`, `TransferRequestValidationTest`, `TransferControllerTest`) cover:

- valid request validation, including recipient email and NUMERIC(19,2) limits
- authenticated sender lookup and rejection of client-supplied sender identity
- successful sender debit and recipient credit
- exactly one TRANSFER/DEBIT and one TRANSFER/CREDIT history entry
- missing recipient account
- self-transfer rejection before any account lock or write
- account locks acquired in ascending user-ID order for both transfer directions
- insufficient funds
- missing, blank, oversized, and invalid idempotency keys
- completed duplicate requests returning the original stored result
- conflicting idempotency-key reuse returning a conflict
- in-progress idempotency-key reuse returning a conflict
- failed transaction-entry persistence propagating an error for transaction rollback
- optimistic-lock conflict propagation and HTTP 409 mapping
- controller propagation of the authenticated identity and optional header behavior

## HTTP transfer scenarios
`TransferControllerMvcTest` runs MockMvc against the real `SecurityConfiguration` and `RestExceptionHandler`, with `TransferService` mocked. It covers:

- HTTP 401 `UNAUTHENTICATED` without a session and HTTP 403 without a CSRF token
- missing and blank `Idempotency-Key` headers returning HTTP 400 `INVALID_IDEMPOTENCY_KEY`
- body validation failures and malformed JSON returning HTTP 400 without calling the service
- HTTP 409 for `IDEMPOTENCY_KEY_CONFLICT`, `OPTIMISTIC_LOCK_CONFLICT`, and `LOCK_CONFLICT`
- HTTP 404 for an unknown recipient and HTTP 400 for insufficient funds
- the exact seven-field success response, with a client-supplied sender field ignored

## Database-backed transfer scenarios
`TransferIntegrationTest` runs against a real PostgreSQL database and verifies:

- V1-V6 Flyway migrations apply to a fresh database and Hibernate `ddl-auto=validate` succeeds.
- Both account updates, both transaction entries, and the idempotency record roll back together when a database write fails (simulated with a temporary trigger).
- A completed retry returns the original result and creates no second pair of ledger entries.
- Concurrent requests with the same sender/key apply only one transfer.
- Concurrent transfers cannot overspend the same sender account.
- Interleaved opposite-direction transfers (A→B and B→A) complete without deadlock.
- Mixed-case emails are normalized across registration, login, and transfer recipients, and the database rejects a non-normalized email.

The opposite-direction test is timing-dependent: it is strong evidence that lock ordering prevents deadlocks, not a formal proof.

### Test database
The integration test uses a dedicated `jcash_banking_test_db` database owned by `jcash_banking_user`. Create it once:
```bash
sudo -u postgres psql -c "CREATE DATABASE jcash_banking_test_db OWNER jcash_banking_user;"
```
**Every run drops and recreates the entire schema of `jcash_banking_test_db`.** The test refuses to clean any database with a different name, so the development database is never wiped. Do not store anything in the test database.

### Running the full suite
`BackendApplicationTests` uses the development database and `TransferIntegrationTest` uses the test database; both need PostgreSQL running and `DB_PASSWORD` exported:
```bash
set -a; . ./.env; set +a
./backend/mvnw -f backend/pom.xml test
```
Without PostgreSQL these tests fail rather than skip. In that case run the non-database tests and report the database-dependent limitation explicitly:
```bash
./backend/mvnw -f backend/pom.xml -Dtest='**/*Test,!BackendApplicationTests,!TransferIntegrationTest' test
```
A database test that did not run is not evidence that rollback, migration, or concurrency behavior passed.

Use deterministic test data. Integration tests should exercise transaction behavior and database constraints, not only mocked service logic. Never use real financial or personal data in test fixtures.

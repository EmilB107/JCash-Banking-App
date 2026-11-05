# Testing Strategy

Automated tests should be developed alongside features, not postponed until the end.

## Core tools
- **JUnit 5:** unit tests
- **Mockito:** isolate services and simulate dependency failures
- **Spring Boot Test:** application-level tests
- **MockMvc:** API and authentication/header behavior tests
- **PostgreSQL integration tests:** migrations, constraints, rollback, and concurrency against a dedicated test database
- **Vitest with Angular TestBed:** component, service, and form tests
- **Postman:** the full API flow against a running backend
- **GitHub Actions:** runs the backend and frontend tests on every push

## Transfer scenarios
The transfer unit tests (`TransferServiceTest`, `TransferRequestValidationTest`, `TransferControllerTest`) cover:

- valid request validation, including the recipient identifier and NUMERIC(19,2) limits
- recipients resolved by email or by mobile number in either format, with the resolved email recorded for idempotency
- authenticated sender lookup and rejection of client-supplied sender identity
- successful sender debit and recipient credit
- exactly one TRANSFER/DEBIT and one TRANSFER/CREDIT history entry
- missing recipient account (by email or mobile number)
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

- V1-V9 Flyway migrations apply to a fresh database and Hibernate `ddl-auto=validate` succeeds.
- Both account updates, both transaction entries, and the idempotency record roll back together when a database write fails (simulated with a temporary trigger).
- A completed retry returns the original result and creates no second pair of ledger entries.
- Concurrent requests with the same sender/key apply only one transfer.
- Concurrent transfers cannot overspend the same sender account.
- Interleaved opposite-direction transfers (A→B and B→A) complete without deadlock.
- Mixed-case emails are normalized across registration, login, and transfer recipients, and the database rejects a non-normalized email.

- Transfers by mobile number reach the recipient in both `09...` and `+639...` formats, and a retry naming the recipient by mobile number after email returns the original result.

The opposite-direction test is timing-dependent: it is strong evidence that lock ordering prevents deadlocks, not a formal proof.

## Transaction history scenarios
- `AccountServiceTest` and `TransferServiceTest`: cash-in entries have `Cash in` details and no transfer link; both entries of a transfer share one reference, name each other as counterparty, and carry `Transfer to ...` / `Transfer from ...` details; separate transfers get separate references.
- `TransactionHistoryIntegrationTest` (PostgreSQL): both users' histories show the linked transfer with matching references and counterparties, an idempotent retry adds no entries or references, details survive a later name change, and the database rejects blank details, transfer entries without a counterparty or reference, cash-in entries with one, and a second DEBIT for the same reference.

## Registration and login scenarios
- `MobileNumberNormalizerTest` and `CreateUserRequestValidationTest`: `+639...` normalization, mobile-number format, and exactly-six-digit PINs.
- `UserServiceTest`: PIN hashing, canonical mobile storage, duplicate email and mobile checks (on registration and profile updates, where the user's own values are allowed), and translation of concurrent unique-constraint violations.
- `PinChangeServiceTest` (real BCrypt, fixed `Clock`): the new PIN is stored hashed, a wrong current PIN is counted and keeps the old PIN, the third wrong PIN locks the account, the correct PIN is rejected while locked, and success after an expired lock resets the count.
- `LoginServiceTest` uses Spring Security's real `DaoAuthenticationProvider` and BCrypt with a fixed `Clock`: email and mobile login, wrong PIN, locking on the third failure, rejection of the correct PIN while locked, automatic unlock after 5 minutes, a fresh count after expiry, reset on success, and unknown identifiers.
- `AuthControllerMvcTest` and `UserControllerMvcTest`: session creation on login, the `/api/users/me` profile without credential or lockout fields, HTTP 401 `INVALID_CREDENTIALS`, HTTP 423 `ACCOUNT_LOCKED`, HTTP 409 `EMAIL_ALREADY_REGISTERED` / `MOBILE_NUMBER_ALREADY_REGISTERED`, HTTP 400 validation, HTTP 403 without CSRF, profile updates (including renaming the session principal after an email change), and PIN changes (HTTP 204, 400 `INCORRECT_PIN`, 423 `ACCOUNT_LOCKED`).
- `CsrfCookieMvcTest`: the real `XSRF-TOKEN` cookie flow; login succeeds with either the raw cookie value or the masked token from `/api/auth/csrf` in `X-XSRF-TOKEN`, and fails with HTTP 403 without the header. It has its own context and never uses the `csrf()` post-processor, which swaps a test repository into the shared filter chain.

Against PostgreSQL:
- `UserRegistrationIntegrationTest` forces registration races deterministically: the second insert is confirmed blocked on the unique index (via `pg_locks`) before the first commits, and must fail as a duplicate email or mobile number. It also checks that the database rejects non-canonical mobile numbers.
- `UserProfileIntegrationTest` covers an email change keeping the same session signed in (and login with the new email and mobile number) while the user's other sessions get HTTP 401 and are ended, 409s for another user's email or mobile number, the changed PIN being required at the next login, and wrong current PINs sharing the login lockout (the counter commits even though the request fails).
- `ApiDocsIntegrationTest` checks that `/v3/api-docs` and Swagger UI are public and that the description lists the API endpoints.
- `AuthIntegrationTest` covers HTTP login by email and both mobile formats with an email session principal, lockout and expiry (by moving `locked_until` into the past), reset on success, duplicate mobile numbers, and 10 concurrent wrong-PIN logins producing exactly 2 HTTP 401s, 8 locked responses, and a counter of 3.

### Test database
The PostgreSQL integration tests (`TransferIntegrationTest`, `UserRegistrationIntegrationTest`, `AuthIntegrationTest`, `UserProfileIntegrationTest`, `TransactionHistoryIntegrationTest`, `ApiDocsIntegrationTest`) share `FreshTestDatabaseConfiguration` and one cached Spring context. They use a dedicated `jcash_banking_test_db` database owned by `jcash_banking_user`. Create it once:
```bash
sudo -u postgres psql -c "CREATE DATABASE jcash_banking_test_db OWNER jcash_banking_user;"
```
**Every run drops and recreates the entire schema of `jcash_banking_test_db`.** The test refuses to clean any database with a different name, so the development database is never wiped. Do not store anything in the test database.

### Running the full suite
`BackendApplicationTests` uses the development database and the integration tests use the test database; all of them need PostgreSQL running and `DB_PASSWORD`, `ACTUATOR_USERNAME`, and `ACTUATOR_PASSWORD` exported (loading `.env` as below does this):
```bash
set -a; . ./.env; set +a
./backend/mvnw -f backend/pom.xml test
```
Without PostgreSQL these tests fail rather than skip. In that case run the non-database tests and report the database-dependent limitation explicitly:
```bash
./backend/mvnw -f backend/pom.xml -Dtest='**/*Test,!BackendApplicationTests,!*IntegrationTest' test
```
A database test that did not run is not evidence that rollback, migration, or concurrency behavior passed.

Use deterministic test data. Integration tests should exercise transaction behavior and database constraints, not only mocked service logic. Never use real financial or personal data in test fixtures.

## Continuous integration
`.github/workflows/ci.yml` runs on every push and pull request to `main`, in two parallel GitHub Actions jobs:
- **backend:** starts a throwaway PostgreSQL 16 service, creates `jcash_banking_test_db`, and runs the full Maven test suite, database tests included.
- **frontend:** runs `npm ci`, the unit tests, and a production build.

Results are on the repository's **Actions** tab, and the badge at the top of the README shows the latest run on `main`. The CI database and passwords are throwaway values that exist only during the run.

## Postman collection
`postman/JCash.postman_collection.json` walks through the whole API against a running backend, in this order: setup (health and CSRF token), registration, authentication (including the 3-attempt lockout), cash-in, transfers, transaction history, profile and PIN change, and the recipient's view of the transfer. Each request has Postman tests that check the status code, the `error` code on failures, and the important response fields.

Every run registers fresh users (Alice, Bob, and Carol) with unique emails and mobile numbers, so you can repeat it against the development database. Those users stay in the database afterwards. The requests depend on each other (later ones use the saved session cookie, CSRF token, and IDs), so always run the whole collection in order, not single requests.

With the backend running on `http://localhost:8080`:

- **Postman app:** Import the file, open the collection, and choose **Run**.
- **Command line:**
  ```bash
  npx newman run postman/JCash.postman_collection.json
  ```

To test another server, change the `baseUrl` collection variable (or pass `--env-var baseUrl=https://...` to newman).

## End-to-end verification
This checks the whole system together: the browser or API client, the backend, and the database. It has two parts.

### 1. Move money between separate users
Either run the [Postman collection](#postman-collection), which registers Alice and Bob, cashes in, transfers, and checks both sides, or do it by hand in the Angular app:

1. Open the app in a normal window and in a private window, so each window has its own session.
2. Register a different user in each window, for example Alice and Bob.
3. As Alice, cash in an amount, then send part of it to Bob by mobile number.
4. As Bob, refresh the dashboard: the balance and the history entry ("Transfer from Alice ...") should appear.
5. As Alice, check the balance went down by exactly the amount sent, and that the history shows the transfer.

### 2. Check the ledger in the database
`scripts/sql/verify-ledger.sql` is a read-only script (SELECTs only) that checks the rules every correct ledger must follow:

| # | Rule | What a failure would mean |
|---|---|---|
| 1 | Account balance = credits − debits | A balance changed without a matching entry, or an entry was written without updating the balance |
| 2 | Each entry's `balance_after` = running balance | Entries were applied out of order or with the wrong amount |
| 3 | Every transfer has both a DEBIT and a CREDIT | Half a transfer committed (money vanished or appeared) |
| 4 | Both halves have the same amount and currency | The recipient got a different amount than the sender paid |
| 5 | Transfer halves point at each other, between different accounts | Broken counterparty links, or a self-transfer slipped through |
| 6 | Entry currency matches its account currency | Mixed currencies in one account |
| 7 | Every user has an account | Registration created a user without an account |
| 8 | Total of all balances = total cash-in | Money was created or destroyed outside cash-in (transfers only move money) |
| 9 | Transfers recorded = completed idempotency keys | A retried request was processed twice |

Run it against the development database (it asks for the `jcash_banking_user` password):
```bash
psql -h localhost -U jcash_banking_user -d jcash_banking_db -f scripts/sql/verify-ledger.sql
```
Every row should say `PASS`. The second table shows totals (users, cash-ins, transfers, and the sum of all balances) for context.

The database constraints already block some of these at write time (for example, a negative balance or two DEBITs for one transfer). The script checks the rules that span several rows or tables, which a single constraint can't enforce.

To see the checks fail, break the data inside a transaction and roll it back, so nothing is saved:
```sql
BEGIN;
UPDATE accounts SET balance = balance + 5 WHERE id = (SELECT id FROM accounts LIMIT 1);
\i scripts/sql/verify-ledger.sql
ROLLBACK;
```
Rules 1 and 8 then report `FAIL`.

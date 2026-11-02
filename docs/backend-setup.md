# Backend Setup

The backend uses **Java 21** and **Spring Boot 4.1.2** with Spring Data JPA, Flyway, Bean Validation, Spring Web MVC, Spring Security, and PostgreSQL. It currently supports:

- user registration with email, Philippine mobile number, and a BCrypt-hashed 6-digit PIN
- session-based login by email or mobile number with PIN, and logout
- account lockout for 5 minutes after 3 consecutive failed logins
- CSRF protection
- OpenAPI description and Swagger UI
- authenticated current-user access
- authenticated account retrieval
- validated demo cash-in with an atomic account update and transaction entry
- authenticated current-user transaction history
- authenticated transfers between separate PHP accounts
- transfer idempotency, ordered pessimistic locking of sender and recipient accounts, optimistic version checks, and atomic ledger persistence
- PHP account creation with a zero balance

## Prerequisites
From the repository root:
```bash
java --version
```
Use the Maven Wrapper in the repository. Confirm `backend/mvnw` and `backend/pom.xml` exist before running the commands below. The POM currently sets `<java.version>21</java.version>`, so Java 21 is the intended runtime. Check the exact runtime with `java --version`.

## Configure database access
The local database guide defines the database and role. The backend reads three environment variables and won't start without all of them:

| Variable | Purpose |
|---|---|
| `DB_PASSWORD` | Password of the `jcash_banking_user` PostgreSQL role |
| `ACTUATOR_USERNAME` | Username of the operator account for `/actuator/metrics` (any name, for example `ops`) |
| `ACTUATOR_PASSWORD` | Password of that operator account |

The usual place for them is the root `.env` file (copied from `.env.example`), which the start scripts and the test command load. To set them only in the terminal that will run Spring Boot instead:
```bash
read -s -p "Enter PostgreSQL password: " DB_PASSWORD
echo
read -s -p "Choose an Actuator password: " ACTUATOR_PASSWORD
echo
export DB_PASSWORD ACTUATOR_PASSWORD ACTUATOR_USERNAME=ops
```
The passwords are hidden while typing. Repeat in each new terminal session. Never commit passwords or other secrets.

### Actuator operator account
This account is only for reading server metrics; it isn't an app user and isn't stored in the database. Pick any username and a long random password, then put both in `.env`:
```text
ACTUATOR_USERNAME=ops
ACTUATOR_PASSWORD=paste-a-generated-password-here
```
To generate a password:
```bash
# Linux / macOS
openssl rand -base64 24
```
```powershell
# Windows PowerShell
[Convert]::ToBase64String((1..24 | ForEach-Object { Get-Random -Maximum 256 }))
```
Restart the backend after changing either value. To use the account, open `http://localhost:8080/actuator/metrics` in a browser and enter the username and password when prompted, or run:
```bash
set -a; . ./.env; set +a
curl -u "$ACTUATOR_USERNAME:$ACTUATOR_PASSWORD" http://localhost:8080/actuator/metrics
```
When deploying, set the same two variables in the host's environment settings, with a different password from your local one.

The `spring.application.name` property identifies the Spring application for configuration and logging; it does not affect the database connection. The project uses `jcash-banking` as its application identifier. The remaining settings match this example:
```properties
spring.application.name=jcash-banking
spring.datasource.url=jdbc:postgresql://localhost:5432/jcash_banking_db
spring.datasource.username=jcash_banking_user
spring.datasource.******
spring.datasource.driver-class-name=org.postgresql.Driver
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.open-in-view=false
spring.flyway.enabled=true
```

This example reflects the `application.properties` content you supplied. The POM declares Flyway and PostgreSQL support, and migration files are stored in `backend/src/main/resources/db/migration/`. Flyway applies the transaction-entry schema migration before Hibernate validates the entity mappings. `ddl-auto=validate` checks that the schema matches the entities; it does not create or update tables.

## Start the backend
From the repository root, the standard Maven Wrapper command is:
```bash
./backend/mvnw -f backend/pom.xml spring-boot:run
```
Alternatively, from `backend/`:
```bash
./mvnw spring-boot:run
```
The server commonly uses `http://localhost:8080`. Confirm startup from the logs. `GET /api/health` and `GET /actuator/health` are public. `/actuator/metrics` needs the operator account from `ACTUATOR_USERNAME` and `ACTUATOR_PASSWORD` in `.env`, sent with HTTP Basic (see [performance tuning](performance-tuning.md)). Stop with `Ctrl+C`.

## Optional convenience script
The repository includes optional convenience scripts. From the repository root, use the platform-specific script:
```bash
# Linux
./scripts/linux/start-backend.sh

# macOS
./scripts/macos/start-backend.sh

# Windows PowerShell
.\scripts\windows\start-backend.ps1
```
The script loads the root `.env`, checks that `DB_PASSWORD`, `ACTUATOR_USERNAME`, and `ACTUATOR_PASSWORD` are set without printing them, and starts the Maven Wrapper from `backend/`. It does not create or alter databases. Database connection and Flyway errors remain in the Spring Boot output.

## Dependencies and scripts currently declared

The provided POM includes Spring Data JPA, Flyway, Bean Validation, Spring Web MVC, Spring Security, and the PostgreSQL JDBC driver. It also includes test-scoped starters for JPA, Flyway, validation, Web MVC, and Spring Security (`spring-boot-starter-security-test`, which provides `spring-security-test` and the security integration for `@WebMvcTest`). The supplied POM uses Spring Boot 4.1.2 and Java 21; keep this guide aligned if you intentionally change either version.

## Authentication and money movement endpoints

The current authentication flow uses server-side HTTP sessions:

1. `GET /api/auth/csrf` to receive the CSRF token and `XSRF-TOKEN` cookie.
2. `POST /api/users` with `email`, `mobileNumber`, `displayName`, and `pin` to register a user and automatically create a PHP account with a zero balance.
3. `POST /api/auth/login` with `identifier` (email or mobile number) and `pin` to authenticate and establish the session.
4. `GET /api/users/me` to retrieve the authenticated user's profile (`id`, `email`, `mobileNumber`, `displayName`, `createdAt`).
5. `GET /api/accounts/me` to retrieve the authenticated user's PHP account summary.
6. `POST /api/accounts/me/cash-in` to increase that account balance with a positive `BigDecimal` amount (at most 100,000.00 per request, with no more than 2 decimal places; anything else returns HTTP 400 `VALIDATION_FAILED`) and record a CREDIT/CASH_IN transaction entry in the same transaction.
7. `GET /api/transactions/me?page=0&size=20` to retrieve one page of the authenticated user's transaction history, ordered newest first. `page` is zero-based (default 0) and `size` is 1–100 (default 20); anything else returns HTTP 400 `INVALID_PAGE_REQUEST`. The response is `{content, page, size, totalElements, totalPages}`, where `content` holds the entries. Each entry has a server-generated `details` description (`Cash in`, `Transfer to <name> (<mobile>)`, or `Transfer from <name> (<mobile>)`) and, for transfers, a `transferReference` shared by the sender's DEBIT and the recipient's CREDIT entry.
8. `POST /api/transfers` to transfer a positive amount to another user's account.
9. `POST /api/auth/logout` to invalidate the session. It returns HTTP 204 (no redirect).

State-changing requests must include the CSRF token in the `X-XSRF-TOKEN` header. Either form is accepted: the masked `token` returned by `GET /api/auth/csrf`, or the raw `XSRF-TOKEN` cookie value (what Swagger UI and Angular send). The `JSESSIONID` cookie identifies the server-side session and should remain inaccessible to client-side JavaScript.

Emails are case-insensitive. Registration, login, and transfer recipients are trimmed and lowercased before use, so `Bob@Example.com` and `bob@example.com` are the same account.

Mobile numbers are required and unique. Registration accepts `09XXXXXXXXX` or `+639XXXXXXXXX` and stores `09XXXXXXXXX`; the PIN must be exactly six digits. A duplicate email returns HTTP 409 `EMAIL_ALREADY_REGISTERED` and a duplicate mobile number returns HTTP 409 `MOBILE_NUMBER_ALREADY_REGISTERED`.

A login identifier containing `@` is treated as an email; anything else is treated as a mobile number in either format. The session principal is always the user's email. A wrong PIN or unknown identifier returns HTTP 401 `INVALID_CREDENTIALS`. The third consecutive wrong PIN locks the account for 5 minutes and returns HTTP 423 `ACCOUNT_LOCKED`; while locked, even the correct PIN returns HTTP 423. The lock expires automatically, and a successful login resets the failure count.

`POST /api/transfers` requires the `Idempotency-Key` header. The key must be nonblank and no longer than 255 characters. The JSON body contains only `recipient` (email or mobile number, resolved like the login identifier) and `amount`; the sender is always derived from the authenticated session identity. The recipient must exist, must have an account, and must not be the sender. A successful response contains sender/recipient account IDs, recipient email, amount, currency, and both resulting balances.

Transfers debit the sender with `Account.debit`, credit the recipient with `Account.credit`, and write one TRANSFER/DEBIT and one TRANSFER/CREDIT entry. Account updates, both entries, and the idempotency record commit in one transaction. A repeated key with the same resolved recipient and amount (even if named by mobile number one time and email the next) returns the original completed response; reuse with different request data or while the original request is in progress returns HTTP 409. Invalid input returns HTTP 400, an unknown recipient returns HTTP 404, insufficient funds or self-transfer returns HTTP 400, an optimistic-lock conflict returns HTTP 409 `OPTIMISTIC_LOCK_CONFLICT`, and a lock-acquisition failure such as a lock timeout returns HTTP 409 `LOCK_CONFLICT`. Both 409 lock responses are safe to retry with the same `Idempotency-Key`. Requests without a session return HTTP 401, and state-changing requests without a CSRF token return HTTP 403.

The sender and recipient account rows are locked in a consistent order (ascending user ID), so simultaneous opposite-direction transfers wait instead of deadlocking.

Account and transaction endpoints do not accept a user ID from the client. The server derives ownership from the authenticated session identity. Account responses expose only the account ID, currency, balance, creation timestamp, and update timestamp. Transaction-history responses expose only the transaction ID, account ID, transaction type, direction, amount, currency, balance after the transaction, details, transfer reference (null for cash-in), and creation timestamp; PIN hashes, JPA entities, and user relationships are not returned.

## API documentation
With the backend running, Swagger UI is at `http://localhost:8080/swagger-ui/index.html` and the OpenAPI description at `/v3/api-docs`. Both are public; every other endpoint keeps its normal protection. To use **Try it out** on protected endpoints, call `GET /api/auth/csrf` and then `POST /api/auth/login` from the page; the browser then sends the session and CSRF cookies, and `springdoc.swagger-ui.csrf.enabled=true` makes Swagger UI copy the `XSRF-TOKEN` cookie into the `X-XSRF-TOKEN` header.

## Configuration principles
- Use environment variables for secrets and deployment-specific configuration.
- Keep database schema changes in versioned Flyway migrations.
- Once a migration has been applied, create a new migration for later changes instead of editing the applied file.
- See [database setup](database-setup.md) for PostgreSQL service and role commands.

## Running tests
The full suite needs PostgreSQL running, `DB_PASSWORD`, `ACTUATOR_USERNAME`, and `ACTUATOR_PASSWORD` exported, and a separate `jcash_banking_test_db` database for the integration tests, whose schema is wiped on every run. See [testing](testing.md#test-database) for the one-time setup command and the non-database fallback.

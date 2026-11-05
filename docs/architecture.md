# Architecture

## Approach
Use a **modular monolith**, not microservices, with a layered backend. Keep business rules in services, persistence in repositories, HTTP handling in controllers, and API contracts in DTOs.

## Feature areas
- **Users and security:** registration/login, authentication, authorization, ownership checks
- **Accounts:** wallet/account details and balances
- **Transfers:** validation and atomic transfer processing
- **Transactions/ledger:** durable history and balanced ledger entries

The backend packages follow these areas:
```text
backend/src/main/java/com/jcash/app/
├── users/
├── accounts/
├── transfers/
├── transactions/
├── security/
└── common/
```
Avoid adding layers or abstractions without a concrete need.

## Assessment requirement mapping

The assessment PDF requires separate `model`, `service`, and `util` responsibilities
and a main application class. The feature-based structure above satisfies those
requirements without forcing all unrelated classes into broad top-level packages:

| Assessment requirement | Project implementation |
|---|---|
| `model` package | Domain and JPA model classes in the relevant feature package, such as `users/User`, `accounts/Account`, and `transactions/TransactionEntry` |
| `service` package | Business services in the relevant feature package, such as authentication, cash-in, transfer, and transaction-history services |
| `util` package | Shared validation, exception handling, security, formatting, and other cross-cutting helpers in `common/` or a focused utility package when needed. Feature-specific exceptions live in each feature's `exception/` subpackage (for example `transfers/exception/`), alongside its `dto/` subpackage |
| Main application class | `com.jcash.app.BackendApplication` |
| User model | User identity, mobile number, name, credential hash, account relationship, and transaction ownership |
| Transaction model | Transaction type, exact monetary amount (`BigDecimal`), details, timestamp, and related account or transfer |
| Login, balance, cash-in, transfer, and transaction logs | REST controllers delegating to validated services and repositories |
| JDBC/database persistence | Spring Data JPA and Hibernate backed by JDBC, PostgreSQL, and versioned Flyway migrations |

Where the assessment wording and common industry practice differ, the project
leans towards industry practice and records the mapping here rather than
restructuring:

- **Packages:** feature packages (`users/`, `accounts/`, `transfers/`,
  `transactions/`) instead of top-level `model/`, `service/`, and `util/`
  folders, so each feature's model, service, DTOs, and exceptions stay together.
- **Identifiers:** `UUID` primary keys instead of `long` user IDs, so IDs are
  not guessable or sequential and can be generated without a database round trip.
- **Model names:** the user's balance and transaction list live on `Account`
  and its `TransactionEntry` ledger rows rather than directly on `User`, so a
  balance always has an auditable ledger behind it.
- **Login messages:** a wrong PIN and an unknown mobile number or email return
  the same message ("Email/mobile number or PIN is incorrect"), so the login
  endpoint cannot be used to discover which mobile numbers are registered. The
  application still checks that the user exists before verifying the PIN, and
  a locked account gets its own message.

This mapping preserves the assessment’s required object-oriented design while
using maintainable feature-based packages. The assessment’s “three login
attempts” requirement is implemented as server-side failure tracking: the user
row is locked for each attempt, and the third consecutive wrong PIN locks the
account for 5 minutes (HTTP 423), rather than a console-only retry loop.

## Request flow
Angular UI → REST API controller → validation/service layer → repository/JPA → PostgreSQL. Responses use DTOs and appropriate HTTP status codes. Centralized exception handling (`common/RestExceptionHandler`) returns consistent error responses without exposing internal details.

## Transaction boundary
A transfer is processed as one database transaction: validate the operation, prevent overspending under concurrent requests, record balanced ledger entries, update both account balances, and commit together. Any failure rolls back all related writes. Monetary values use `BigDecimal`, and the `Idempotency-Key` header makes transfer requests safe to retry.

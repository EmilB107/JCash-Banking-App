# Architecture

## Approach
Use a **modular monolith**, not microservices, with a layered backend. Keep business rules in services, persistence in repositories, HTTP handling in controllers, and API contracts in DTOs.

## Planned feature areas
- **Users and security:** registration/login, authentication, authorization, ownership checks
- **Accounts:** wallet/account details and balances
- **Transfers:** validation and atomic transfer processing
- **Transactions/ledger:** durable history and balanced ledger entries

A possible backend package structure is:
```text
backend/src/main/java/.../
├── users/
├── accounts/
├── transfers/
├── transactions/
├── security/
└── common/
```
Adapt package names to the actual Spring Boot base package. Avoid adding layers or abstractions without a concrete need.

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

This mapping preserves the assessment’s required object-oriented design while
using maintainable feature-based packages. The assessment’s “three login
attempts” requirement is implemented as server-side failure tracking: the user
row is locked for each attempt, and the third consecutive wrong PIN locks the
account for 5 minutes (HTTP 423), rather than a console-only retry loop.

## Request flow
Angular UI → REST API controller → validation/service layer → repository/JPA → PostgreSQL. Responses should use DTOs and appropriate HTTP status codes. Centralized exception handling should provide consistent error responses without exposing internal details.

## Transaction boundary
A transfer should be processed as one database transaction: validate the operation, prevent overspending under concurrent requests, record balanced ledger entries, update any maintained balance representation, and commit together. Failure must roll back all related writes. Use `BigDecimal` for monetary values and define an idempotency strategy for retry-safe requests.

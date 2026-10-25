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

## Request flow
Angular UI → REST API controller → validation/service layer → repository/JPA → PostgreSQL. Responses should use DTOs and appropriate HTTP status codes. Centralized exception handling should provide consistent error responses without exposing internal details.

## Transaction boundary
A transfer should be processed as one database transaction: validate the operation, prevent overspending under concurrent requests, record balanced ledger entries, update any maintained balance representation, and commit together. Failure must roll back all related writes. Use `BigDecimal` for monetary values and define an idempotency strategy for retry-safe requests.

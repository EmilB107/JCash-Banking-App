# JCash Banking

JCash is a full-stack **simulated wallet** project for learning and demonstrating frontend development, backend design, database modeling, authentication, transaction integrity, concurrency control, and automated testing. It is not a real banking or payment service and must not be used to store real financial information or move real money.

## Technology
- **Frontend:** Angular 22.2.x, TypeScript 6.0.x, Angular Router, HttpClient, Reactive Forms
- **Backend:** Java 21, Spring Boot 4.1.2, Spring Web MVC, Spring Data JPA, Bean Validation, Spring Security with server-side sessions and CSRF protection
- **Database:** PostgreSQL 16, Spring Data JPA/Hibernate, Flyway; JDBC where appropriate
- **Build and version control:** Maven Wrapper, npm 11.19.1, Git, GitHub
- **API and testing:** REST, Postman, Swagger UI/OpenAPI, JUnit 5, Mockito, Spring Boot Test

## Intended capabilities
- Registration/login and access restricted to each user's own account
- Wallet balances, transfers, and transaction history
- Atomic transfers with concurrency protection and idempotency
- Auditable double-entry ledger and database integrity constraints
- Automated tests and a publicly accessible portfolio demo

The current backend includes user registration, password hashing, session-based
login/logout, CSRF protection, automatic creation of a PHP account with a zero
balance, authenticated account retrieval, validated demo cash-in, authenticated
transaction history, and authenticated transfers through `POST /api/transfers`.
Transfers derive the sender from the session, require an `Idempotency-Key`
header, write balanced debit/credit entries atomically, and return the original
result for a completed duplicate request.

## Repository layout
```text
JCash-Banking-App/
├── scripts/       # Linux, macOS, and Windows development launchers
├── backend/       # Java and Spring Boot application
├── frontend/      # Angular application
└── docs/          # Setup and technical documentation
```

## Documentation
- [Development environment overview](docs/dev-env.md)
- [Frontend setup](docs/frontend-setup.md)
- [Backend setup](docs/backend-setup.md)
- [Database setup](docs/database-setup.md)
- [Architecture](docs/architecture.md)
- [Database design](docs/database-design.md)
- [Security](docs/security.md)
- [Testing](docs/testing.md)
- [Deployment](docs/deployment.md)
- [Development roadmap](docs/dev-roadmap.md)

## Local development
Set up PostgreSQL first, copy `.env.example` to `.env`, and set the local PostgreSQL password in `DB_PASSWORD`. On Linux Mint, the complete stack can then be started with:
```bash
./scripts/linux/start-all.sh
```
The launcher starts only the PostgreSQL service and application processes it owns; it does not create, reset, or delete database data. Individual component commands and Windows/macOS instructions are in [Development environment overview](docs/dev-env.md).

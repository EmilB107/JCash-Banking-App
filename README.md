# JCash Banking

[![CI](https://github.com/EmilB107/JCash-Banking-App/actions/workflows/ci.yml/badge.svg)](https://github.com/EmilB107/JCash-Banking-App/actions/workflows/ci.yml)

JCash is a full-stack **simulated wallet** project for learning and demonstrating frontend development, backend design, database modeling, authentication, transaction integrity, concurrency control, and automated testing. It is not a real banking or payment service and must not be used to store real financial information or move real money.

## Live demo
- **App:** https://jcash-cwha.onrender.com
- **API docs (Swagger UI):** https://jcash-api.onrender.com/swagger-ui/index.html

The demo runs on free plans. Pages load right away, but the backend sleeps when idle, so the first sign-in or API call after a quiet spell can take about a minute while it starts. Register with any made-up email and mobile number; all money is simulated. See [Deployment](docs/deployment.md) for how it's hosted.

## Technology
- **Frontend:** Angular 22.2.x, TypeScript 6.0.x, Angular Router, HttpClient, Reactive Forms
- **Backend:** Java 21, Spring Boot 4.1.2, Spring Web MVC, Spring Data JPA, Bean Validation, Spring Security with server-side sessions and CSRF protection
- **Database:** PostgreSQL (16 locally, Supabase-hosted 17 for the demo), Spring Data JPA/Hibernate, Flyway; JDBC where appropriate
- **Build and version control:** Maven Wrapper, npm 11.19.1, Git, GitHub
- **API and testing:** REST, Postman, Swagger UI/OpenAPI, JUnit 5, Mockito, Spring Boot Test
- **Deployment:** Docker, Render (static site and web service), Supabase, GitHub Actions (CI)

## Features
- **Accounts:** registration with email, mobile number, and a BCrypt-hashed 6-digit PIN; each user gets a PHP wallet account with a zero balance
- **Sign-in:** session-based login and logout by email or mobile number, CSRF protection, and a 5-minute lockout after 3 wrong PINs
- **Wallet:** dashboard with balance and recent activity, demo cash-in (up to 100,000.00), and money transfers by the recipient's email or mobile number
- **History:** paged transaction history with expandable details for both sides of a transfer
- **Profile:** edit display name, email, and mobile number; change the PIN
- **Integrity:** each transfer writes balanced debit/credit ledger entries in one database transaction, with protection against concurrent overspending and deadlocks; the required `Idempotency-Key` header makes a retried transfer return the original result instead of moving money twice
- **Access control:** users can only see and use their own account; the sender always comes from the session
- **Testing:** unit, HTTP (MockMvc), and PostgreSQL-backed integration tests, a Postman collection for the full API flow, and a ledger consistency check script

## Repository layout
```text
JCash-Banking-App/
├── scripts/       # Linux, macOS, and Windows development launchers
├── backend/       # Java and Spring Boot application
├── frontend/      # Angular application
├── postman/       # Postman collection for the API
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
Requirements: Java 21, Node.js 24, and PostgreSQL 16.

1. Create the database and its user ([Database setup](docs/database-setup.md)).
2. Copy `.env.example` to `.env` and fill in `DB_PASSWORD` (your local PostgreSQL password) plus `ACTUATOR_USERNAME` and `ACTUATOR_PASSWORD` ([Backend setup](docs/backend-setup.md#actuator-operator-account)).
3. Install the frontend packages once: `cd frontend && npm ci`.
4. From the repository root, start everything:

   **Windows (PowerShell)**
   ```powershell
   .\scripts\windows\start-all.ps1
   ```
   **macOS**
   ```bash
   ./scripts/macos/start-all.sh
   ```
   **Linux**
   ```bash
   ./scripts/linux/start-all.sh
   ```

The app opens at `http://localhost:4200` and the API at `http://localhost:8080`. The launcher starts PostgreSQL and the app but never creates, resets, or deletes data. To start each part separately, see [Development environment overview](docs/dev-env.md).

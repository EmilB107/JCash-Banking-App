# Development Environment

This guide explains the local workflow on **Linux Mint** and links to component-specific instructions. It avoids duplicating the full setup steps maintained in each guide.

## Requirements
- Node.js `^22.22.3`, `^24.15.0`, or `>=26.0.0`, plus npm 11.19.1 (per the supplied frontend package metadata and lockfile)
- Java 21
- PostgreSQL 16 and `psql`
- Git
- The Maven Wrapper supplied by the backend project

Check installed tools with `node --version`, `npm --version`, `java --version`, `psql --version`, and `git --version`.

## Setup guides
1. [Database setup](database-setup.md): install/check PostgreSQL, create the database and application role, and test the connection.
2. [Backend setup](backend-setup.md): configure Java/Spring Boot, `DB_PASSWORD`, JPA, and Flyway.
3. [Frontend setup](frontend-setup.md): install locked npm dependencies with `npm ci` and run Angular.

## Typical startup order
1. Start PostgreSQL and confirm its cluster is online (`pg_lsclusters`).
2. Start the backend with `DB_PASSWORD` available in that terminal.
3. Start the frontend in a separate terminal.
4. Stop frontend and backend with `Ctrl+C`; stop PostgreSQL only when you no longer need it.

The database name is `jcash_banking_db`; the application role is `jcash_banking_user`. Keep the password out of source control. The backend POM is configured for Spring Boot 4.1.2 and Java 25. Your current `application.properties` sets `spring.application.name=backend`; this is valid, though `jcash_banking` would be more consistent with the project name. The datasource uses `jcash_banking_db`, `jcash_banking_user`, and the `DB_PASSWORD` environment variable. The supplied POM does not yet declare Spring Security.

## Troubleshooting checklist
- **Database offline:** check `pg_lsclusters` and start PostgreSQL if needed.
- **Connection refused:** confirm host, port, and service status.
- **Authentication failed:** ensure `DB_PASSWORD` is set in the backend's terminal and matches the role password.
- **Schema validation or migration failure:** inspect backend logs and Flyway migration files; see [database setup](database-setup.md).
- **Frontend cannot reach API:** confirm the backend is running and the frontend's API base URL/CORS settings match the local setup.
- **Port conflict:** inspect listening ports with `sudo ss -ltnp`.

Do not assume `/api/health` exists until it is implemented in the backend; verify the controller and endpoint before documenting or relying on it.

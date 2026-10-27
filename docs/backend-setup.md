# Backend Setup

The supplied `backend/pom.xml` configures **Java 21** and **Spring Boot 4.1.2**. Its declared dependencies include Spring Data JPA, Flyway, Bean Validation, Spring Web MVC, and the PostgreSQL driver. The POM you provided does **not** currently declare Spring Security, so authentication/authorization dependencies still need to be added when implementing that part of the project. DTOs and the layered modular-monolith design are architectural choices, not automatically provided by these dependencies.

## Prerequisites
From the repository root:
```bash
java --version
```
Use the Maven Wrapper in the repository. Confirm `backend/mvnw` and `backend/pom.xml` exist before running the commands below. The POM currently sets `<java.version>21</java.version>`, so Java 21 is the intended runtime. Check the exact runtime with `java --version`.

## Configure database access
The local database guide defines the database and role. The backend reads the database password from the `DB_PASSWORD` environment variable. In the terminal that will run Spring Boot:
```bash
read -s -p "Enter PostgreSQL password: " DB_PASSWORD
echo
export DB_PASSWORD
```
The password is hidden while typing. Repeat in each new terminal session. Never commit passwords or other secrets.

The `spring.application.name` property identifies the Spring application for configuration and logging; it does not affect the database connection. The project uses `jcash-banking` as its application identifier. The remaining settings match this example:
```properties
spring.application.name=jcash-banking
spring.datasource.url=jdbc:postgresql://localhost:5432/jcash_banking_db
spring.datasource.username=jcash_banking_user
spring.datasource.password=${DB_PASSWORD}
spring.datasource.driver-class-name=org.postgresql.Driver
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.open-in-view=false
spring.flyway.enabled=true
```
This example reflects the `application.properties` content you supplied. The POM declares Flyway and PostgreSQL support, but it does not confirm that migration files already exist. `ddl-auto=validate` checks that the schema matches the entities; it does not create or update tables. Flyway migrations belong in `backend/src/main/resources/db/migration/`.

## Start the backend
From the repository root, the standard Maven Wrapper command is:
```bash
./backend/mvnw -f backend/pom.xml spring-boot:run
```
Alternatively, from `backend/`:
```bash
./mvnw spring-boot:run
```
The server commonly uses `http://localhost:8080`. Confirm startup from the logs. Only test a health URL after confirming that the endpoint is implemented. Stop with `Ctrl+C`.

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
The script loads the root `.env`, validates `DB_PASSWORD` without printing it, and starts the Maven Wrapper from `backend/`. It does not create or alter databases. Database connection and Flyway errors remain in the Spring Boot output.

## Dependencies and scripts currently declared

The provided POM includes Spring Data JPA, Flyway, Bean Validation, Spring Web MVC, and the PostgreSQL JDBC driver. It also includes test-scoped starters for JPA, Flyway, validation, and Web MVC. It does not currently include `spring-boot-starter-security`, so don't describe Spring Security as installed until you add the dependency. The supplied POM uses Spring Boot 4.1.2 and Java 21; keep this guide aligned if you intentionally change either version.

## Configuration principles
- Use environment variables for secrets and deployment-specific configuration.
- Keep database schema changes in versioned Flyway migrations.
- Once a migration has been applied, create a new migration for later changes instead of editing the applied file.
- See [database setup](database-setup.md) for PostgreSQL service and role commands.

# Database Setup

JCash uses PostgreSQL 16 locally; the deployed demo uses Supabase-hosted PostgreSQL (see [deployment](deployment.md#database-supabase)). The local database is named `jcash_banking_db`; the application role is `jcash_banking_user`.

## Start and verify PostgreSQL
```bash
psql --version
pg_lsclusters
```
Start or inspect the service on Linux Mint:
```bash
sudo systemctl start postgresql
sudo systemctl status postgresql
pg_lsclusters
```
Use `pg_lsclusters` to check that the relevant cluster is `online`. To stop PostgreSQL when it is no longer needed:
```bash
sudo systemctl stop postgresql
```

## Optional convenience script
The repository includes optional convenience scripts. From the repository root, use the platform-specific script:

**Windows (PowerShell)**
```powershell
.\scripts\windows\start-db.ps1
```
**macOS**
```bash
./scripts/macos/start-db.sh
```
**Linux**
```bash
./scripts/linux/start-db.sh
```
The script checks that PostgreSQL is installed and ready on `localhost:5432`. It does not create, reset, or delete databases. On Windows, service names depend on the PostgreSQL installer; the script starts a discoverable `postgresql*` service when possible. On macOS, it only checks readiness because PostgreSQL may have been installed by Homebrew, Postgres.app, or another distribution.

## Create database and application role
Open the PostgreSQL shell as the administrative account:
```bash
sudo -u postgres psql
```
If they do not already exist, create the role and database (choose a strong password interactively or substitute a secure local value):
```sql
CREATE USER jcash_banking_user WITH PASSWORD 'replace-with-a-local-password';
CREATE DATABASE jcash_banking_db OWNER jcash_banking_user;
```
Exit with `\q`. Do not rerun creation commands for objects that already exist. If the role already exists and you need to change its password, use `\password jcash_banking_user` in `psql` instead of placing the password in shell history.

## Test the connection
```bash
psql -h localhost -U jcash_banking_user -d jcash_banking_db
```
Enter the password when prompted, then run:
```sql
SELECT current_database(), current_user;
```
Exit with `\q`.

## Schema migrations
Flyway manages the schema. Migration files are in `backend/src/main/resources/db/migration/`, with versioned names such as `V1__init_schema.sql`, and run automatically when the backend starts. Hibernate only validates the schema (`spring.jpa.hibernate.ddl-auto=validate`); schema changes go in a new Flyway migration, never through Hibernate auto-update. The application role needs the privileges to create and alter tables; `jcash_banking_user` has them because it owns the database.

## Troubleshooting
- **Connection refused:** start PostgreSQL and verify host `localhost` and port `5432` (unless configured otherwise).
- **Password authentication failed:** check the role password and `DB_PASSWORD` in the backend terminal.
- **Database missing:** list databases with `sudo -u postgres psql -c '\l'`.
- **Migration/schema failure:** read the backend error log and check migration SQL, order, permissions, and entity/schema consistency.

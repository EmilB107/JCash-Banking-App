# Database Setup

JCash uses PostgreSQL 16 locally and plans to use Supabase-hosted PostgreSQL for deployment. The local database is named `jcash_banking_db`; the application role is `jcash_banking_user`.

## Verify PostgreSQL
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
Flyway is the planned schema migration tool. Migration files belong in `backend/src/main/resources/db/migration/`, with versioned names such as `V1__init_schema.sql`. Hibernate is configured to validate the schema (`spring.jpa.hibernate.ddl-auto=validate`); Flyway, not Hibernate auto-update, should manage schema changes. Ensure the application role has the privileges needed to run migrations.

## Troubleshooting
- **Connection refused:** start PostgreSQL and verify host `localhost` and port `5432` (unless configured otherwise).
- **Password authentication failed:** check the role password and `DB_PASSWORD` in the backend terminal.
- **Database missing:** list databases with `sudo -u postgres psql -c '\l'`.
- **Migration/schema failure:** read the backend error log and check migration SQL, order, permissions, and entity/schema consistency.

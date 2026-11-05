# Development Environment

This is the short overview for local development on Windows, macOS, and Linux. Use the component guides for detailed setup:

- [Database setup](database-setup.md)
- [Backend setup](backend-setup.md)
- [Frontend setup](frontend-setup.md)

## Prerequisites

- Java 21
- Node.js `^22.22.3`, `^24.15.0`, or `>=26.0.0`
- npm 11.19.1
- PostgreSQL 16 and `psql`
- Git

Check installed versions:

```bash
java --version
node --version
npm --version
psql --version
```

The backend uses the Maven Wrapper in `backend/`; a global Maven installation is not required.

## Initial setup

From the repository root:

```bash
cp .env.example .env
```

Set the existing PostgreSQL role password and an operator account for Actuator metrics in `.env`:

```text
DB_PASSWORD=your-local-postgresql-password
ACTUATOR_USERNAME=ops
ACTUATOR_PASSWORD=a-long-random-password
```

Generate the Actuator password with `openssl rand -base64 24` (Linux/macOS) or see [Backend setup](backend-setup.md#actuator-operator-account) for Windows. The backend won't start if any of the three is missing.

The `.env` file is ignored by Git. Install frontend dependencies once:

```bash
cd frontend
npm ci
cd ..
```

## Start the full stack

The standard component commands are documented in the linked guides. The optional combined launchers start PostgreSQL first, then the backend and frontend:

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

On every platform, the backend normally uses `http://localhost:8080` and the frontend uses `http://localhost:4200`. Press `Ctrl+C` to stop the application processes started by the combined launcher. PostgreSQL remains running.

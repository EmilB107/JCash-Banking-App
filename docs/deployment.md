# Deployment Plan

The intended portfolio deployment is:
- **Frontend:** Angular on Vercel (Netlify is an alternative)
- **Backend:** Spring Boot on Render
- **Database:** PostgreSQL on Supabase
- **Repository:** GitHub monorepo
- **API documentation:** Swagger UI/OpenAPI, where enabled

This is a target architecture, not a claim that deployment is already configured. Check provider pricing, free-tier limits, sleeping/pausing behavior, backups, and data retention before launch.

## Database (Supabase)
The Supabase project was created with these choices:
- **Region:** Southeast Asia (Singapore), the same region as the backend, because every API request makes several database calls.
- **Data API off:** it would publish the tables as a public REST API. Only the backend talks to the database, over JDBC, and it does the access checks itself.
- **Automatic RLS off:** Row Level Security protects the Data API, which is off.
- **No GitHub integration:** Flyway owns the schema and runs the migrations when the backend starts.

The backend connects through Supabase's **Session pooler**, because the free plan's direct connection only works over IPv6 and Render only connects over IPv4. Set these in the backend host's environment variables:

| Variable | Value |
|---|---|
| `DB_URL` | `jdbc:postgresql://<pooler host>:5432/postgres?sslmode=require` |
| `DB_USERNAME` | `postgres.<project ref>` (shown in Supabase under **Connect**) |
| `DB_PASSWORD` | The database password chosen when creating the project |

Keep the password out of `DB_URL` so it never needs percent-encoding and isn't printed with the URL. `sslmode=require` encrypts the connection over the internet.

## Backend (Render)
Render has no built-in Java runtime, so the backend is deployed with `backend/Dockerfile`: it builds the jar with the Maven Wrapper (tests skipped, since they need PostgreSQL) and runs it on a Java 21 runtime image as a non-root user.

Create a **Web Service** from the GitHub repository with:

| Setting | Value |
|---|---|
| Language | Docker |
| Root Directory | `backend` |
| Region | Singapore (same as the database) |
| Instance type | Free |
| Health Check Path | `/api/health` |

Environment variables: `DB_URL`, `DB_USERNAME` and `DB_PASSWORD` from the [database section](#database-supabase), plus `ACTUATOR_USERNAME` and a new long random `ACTUATOR_PASSWORD`. Render sets `PORT` itself, and `server.port=${PORT:8080}` picks it up.

The free instance has only 0.1 CPU, which made Spring Boot start about 23 times slower than on a PC, so slowly that Supabase's pooler closed the first encrypted connection before Java finished setting it up. The Dockerfile therefore starts Java with lighter JIT and garbage-collector settings and a Class Data Sharing archive made during the build (together about 4× less startup CPU), and HikariCP retries the first connection for up to 60 seconds (`initialization-fail-timeout`).

The free instance sleeps after about 15 minutes without traffic, and the next request waits for the app to start again (around a minute). Supabase's free Session pooler allows only a small number of connections, so don't run a local backend against Supabase while the Render one is running: each holds up to 10 (the HikariCP pool size).

## Deployment checklist
- Configure the frontend API base URL for the deployed backend.
- Configure the backend datasource with Supabase connection details and secrets stored in hosting environment variables.
- Configure CORS to allow only the intended frontend origin.
- Set `ACTUATOR_USERNAME` and a long random `ACTUATOR_PASSWORD` in the hosting environment variables; `/actuator/health` stays public, and metrics need that account.
- Use HTTPS for public traffic.
- Apply Flyway migrations through a controlled deployment process.
- Confirm authentication, authorization, ownership checks, and transfer integrity before making the demo public.
- Provide synthetic demo data or a demo account without exposing personal information.
- Keep credentials out of GitHub and logs.
- Include screenshots, setup documentation, and live demo links when available.
- Display a clear disclaimer that JCash simulates wallet transfers and does not handle real money.

Introduce Docker/Docker Compose only after the core app works locally, unless a concrete deployment need makes containers useful earlier.

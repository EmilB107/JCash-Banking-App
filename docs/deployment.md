# Deployment

The portfolio demo runs on free plans:

| Part | Host | URL |
|---|---|---|
| Frontend (Angular) | Render Static Site (global CDN) | https://jcash-cwha.onrender.com |
| Backend (Spring Boot) | Render Web Service, Docker, Singapore | https://jcash-api.onrender.com |
| Database (PostgreSQL) | Supabase, Singapore | — |

```
Browser ──> Static Site ──/api/*──> Web Service ──> Supabase (Session pooler)
```

Visitors only ever talk to the static site. It forwards `/api/*` to the backend, the same way `ng serve` proxies `/api` locally, so the browser sees one origin: the session and CSRF cookies are first-party and no CORS configuration is needed. Swagger UI is also available on the backend at `/swagger-ui/index.html`.

Free plans change; check their current limits (sleeping, pausing, connection counts, backups) from time to time.

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

Environment variables: `DB_URL`, `DB_USERNAME` and `DB_PASSWORD` from the [database section](#database-supabase), plus `ACTUATOR_USERNAME`, a new long random `ACTUATOR_PASSWORD`, and `SERVER_SERVLET_SESSION_COOKIE_SECURE=true`. That last one marks the session cookie `Secure`; it's set only here so local development keeps working over plain HTTP. Render sets `PORT` itself, and `server.port=${PORT:8080}` picks it up.

The free instance has only 0.1 CPU, which made Spring Boot start about 23 times slower than on a PC, so slowly that Supabase's pooler closed the first encrypted connection before Java finished setting it up. The Dockerfile therefore starts Java with lighter JIT and garbage-collector settings and a Class Data Sharing archive made during the build (together about 4× less startup CPU), and HikariCP retries the first connection for up to 60 seconds (`initialization-fail-timeout`).

The free instance sleeps after about 15 minutes without traffic, and the next request waits for the app to start again (around a minute). Supabase's free Session pooler allows only a small number of connections, so don't run a local backend against Supabase while the Render one is running: each holds up to 10 (the HikariCP pool size).

## Frontend (Render Static Site)

| Setting | Value |
|---|---|
| Root Directory | `frontend` |
| Build Command | `npm ci && npm run build` |
| Publish Directory | `dist/frontend/browser` |
| Environment variable | `NODE_VERSION=24` |
| Build Filters, Included Paths | `frontend/**` |

**Redirects/Rewrites**, in this order (rules are checked top to bottom):

| Source | Destination | Action | Why |
|---|---|---|---|
| `/api/*` | `https://jcash-api.onrender.com/api/*` | Rewrite | Forwards API calls to the backend |
| `/*` | `/index.html` | Rewrite | Lets Angular handle page URLs like `/history`, so a refresh doesn't 404 |

The frontend needs no code changes for production: it already calls relative `/api/...` URLs.

## Deployment checklist
- [x] Route API calls from the frontend to the backend (static-site rewrite of `/api/*`; no CORS needed).
- [x] Configure the backend datasource with Supabase connection details and secrets stored in hosting environment variables.
- [x] Set `ACTUATOR_USERNAME` and a long random `ACTUATOR_PASSWORD` in the hosting environment variables; `/actuator/health` stays public, and metrics need that account.
- [x] Use HTTPS for public traffic (Render provides it; cookies are marked `Secure`).
- [x] Apply Flyway migrations through a controlled deployment process (they run when the backend starts).
- [x] Confirm authentication, authorization, ownership checks, and transfer integrity before making the demo public.
- [x] Provide synthetic demo data or a demo account without exposing personal information.
- [x] Keep credentials out of GitHub and logs.
- [x] Include screenshots, setup documentation, and live demo links.
- [x] Display a clear disclaimer that JCash simulates wallet transfers and does not handle real money.

Docker is used only to build and run the backend on Render (`backend/Dockerfile`); local development still runs without it.

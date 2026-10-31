# Deployment Plan

The intended portfolio deployment is:
- **Frontend:** Angular on Vercel (Netlify is an alternative)
- **Backend:** Spring Boot on Render
- **Database:** PostgreSQL on Supabase
- **Repository:** GitHub monorepo
- **API documentation:** Swagger UI/OpenAPI, where enabled

This is a target architecture, not a claim that deployment is already configured. Check provider pricing, free-tier limits, sleeping/pausing behavior, backups, and data retention before launch.

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

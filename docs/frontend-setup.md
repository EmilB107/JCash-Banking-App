# Frontend Setup

The supplied `frontend/package.json` declares Angular `^22.2.0` (with Angular CLI/build `^22.2.4`), TypeScript `~6.0.2`, and npm `11.19.1` as its package-manager version. Angular Router, HttpClient, and Reactive Forms are available through the declared Angular packages. Login, dashboard, transfer, and transaction-history pages remain intended application features, not confirmed implemented features.

## Prerequisites
The supplied lockfile is present and uses lockfile version 3. The locked Angular CLI requires Node.js `^22.22.3`, `^24.15.0`, or `>=26.0.0`; use npm 11.19.1 to match the declared package manager. Check the installed versions with:
```bash
node --version
npm --version
```

## Install dependencies
From the repository root:
```bash
cd frontend
```
Because `frontend/package-lock.json` is present, use the reproducible install command:
```bash
npm ci
```
Keep `package-lock.json` committed alongside `package.json`. Use `npm install` when intentionally changing dependencies or updating the lockfile.

## Run Angular
From `frontend/`, the standard npm command is:
```bash
npm start
```

## Optional convenience script
The repository includes optional convenience scripts. From the repository root, use the platform-specific script:
```bash
# Linux
./scripts/linux/start-frontend.sh

# macOS
./scripts/macos/start-frontend.sh

# Windows PowerShell
.\scripts\windows\start-frontend.ps1
```
The script checks that the locked frontend dependencies are installed, then runs the existing `npm start` script (`ng serve`). It does not require the backend to be running.
Other declared scripts are `npm run build`, `npm run watch`, and `npm test`. The test script runs `ng test` (Vitest with jsdom); `npx ng test --watch=false` runs the suite once.

`ng test` loads `vitest-base.config.mts` (enabled by `runnerConfig` in `angular.json`), which replaces `@lucide/angular` with `testing/lucide-angular.stub.ts` during tests only. The real package compiles all of its ~1,600 icon components on import under the test runner, which made each spec that renders an icon ~15 s slower. When the app starts using a new Lucide icon, add a matching one-line stub there; the production build always uses the real package.
The development server commonly uses `http://localhost:4200`. Use the address printed by the CLI if it selects another port. Stop it with `Ctrl+C`.

## API connection
The frontend calls the API with relative `/api/...` URLs. During local development, `ng serve` proxies `/api` to the Spring Boot server at `http://localhost:8080` (see `frontend/proxy.conf.json`), so the browser sees a single origin: session and CSRF cookies work without CORS configuration. Start the backend before using features that call the API.

The API plumbing lives in `frontend/src/app/core/`:

- `http/csrf.interceptor.ts` adds the `X-XSRF-TOKEN` header to state-changing `/api/` requests. It reads the `XSRF-TOKEN` cookie and, when the cookie is missing (first visit, or after logout), fetches one from `GET /api/auth/csrf`. Angular's built-in XSRF support is disabled because it never fetches a missing token.
- `auth/auth.service.ts` holds the signed-in user as a signal (`undefined` = not yet checked, `null` = signed out) and exposes `login`, `register`, `logout`, `loadCurrentUser`, `updateProfile`, and `changePin`. The session is restored from `GET /api/users/me` when the app starts.
- `auth/session-expired.interceptor.ts` clears the signed-in state when any API call other than login returns 401.
- `http/api-error.ts` converts HTTP failures to the backend's `{error, message}` shape, including network failures (`NETWORK_ERROR`).
- `browser/tab-return.ts`: `onTabReturn()` reloads the balance on the dashboard, cash-in, and send-money pages when the user returns to the tab, since changes made in another tab don't show up otherwise.

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
Other declared scripts are `npm run build`, `npm run watch`, and `npm test`. The test script runs `ng test`; verify the generated Angular test configuration before relying on it.
The development server commonly uses `http://localhost:4200`. Use the address printed by the CLI if it selects another port. Stop it with `Ctrl+C`.

## API connection
Configure the frontend API base URL for the environment being used. During local development it will normally point to the local Spring Boot server (commonly port `8080`). Configure backend CORS for the frontend origin. Confirm the actual environment files and API URL in the repository rather than assuming a specific file or endpoint exists.

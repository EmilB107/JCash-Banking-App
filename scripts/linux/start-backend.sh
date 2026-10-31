#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ENV_FILE="$ROOT_DIR/.env"
BACKEND_DIR="$ROOT_DIR/backend"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "Missing $ENV_FILE. Copy .env.example to .env and set DB_PASSWORD, ACTUATOR_USERNAME, and ACTUATOR_PASSWORD." >&2
  exit 1
fi
if [[ ! -s "$ENV_FILE" ]]; then
  echo "$ENV_FILE is empty; set DB_PASSWORD, ACTUATOR_USERNAME, and ACTUATOR_PASSWORD before starting the backend." >&2
  exit 1
fi

# The repository-local .env is intentionally loaded only into this process.
set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a
for required in DB_PASSWORD ACTUATOR_USERNAME ACTUATOR_PASSWORD; do
  if [[ -z "${!required:-}" ]]; then
    echo "$required is missing or empty in $ENV_FILE." >&2
    exit 1
  fi
done
if [[ ! -x "$BACKEND_DIR/mvnw" ]]; then
  echo "Maven Wrapper is missing or not executable: $BACKEND_DIR/mvnw" >&2
  echo "Run: chmod +x backend/mvnw" >&2
  exit 1
fi

if command -v pg_isready >/dev/null 2>&1 && ! pg_isready -h localhost -p 5432 >/dev/null 2>&1; then
  echo "Warning: PostgreSQL is not ready on localhost:5432; Spring Boot may fail to connect." >&2
fi

cd "$BACKEND_DIR"
echo "Starting backend with Maven Wrapper from $BACKEND_DIR."
exec ./mvnw spring-boot:run

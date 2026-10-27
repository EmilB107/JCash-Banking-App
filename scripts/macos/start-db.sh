#!/usr/bin/env bash
set -euo pipefail

command -v pg_isready >/dev/null 2>&1 || {
  echo "pg_isready is required. Install PostgreSQL using your chosen distribution." >&2
  exit 1
}
if ! pg_isready -h localhost -p 5432 >/dev/null 2>&1; then
  echo "PostgreSQL is not ready on localhost:5432." >&2
  echo "Start it using the service manager provided by your PostgreSQL installation, then retry." >&2
  exit 1
fi
echo "PostgreSQL is ready on localhost:5432."

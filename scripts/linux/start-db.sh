#!/usr/bin/env bash
set -euo pipefail

if ! command -v systemctl >/dev/null 2>&1; then
  echo "systemctl is required to manage PostgreSQL on Linux." >&2
  exit 1
fi
if ! command -v pg_isready >/dev/null 2>&1; then
  echo "pg_isready is required. Install the PostgreSQL client tools." >&2
  exit 1
fi

if ! systemctl is-active --quiet postgresql; then
  echo "PostgreSQL is not running; requesting the system service start (sudo may prompt)."
  sudo systemctl start postgresql
fi

if ! pg_isready -h localhost -p 5432 >/dev/null 2>&1; then
  echo "PostgreSQL service is running, but localhost:5432 is not ready." >&2
  echo "Check the cluster with: pg_lsclusters" >&2
  exit 1
fi

echo "PostgreSQL is ready on localhost:5432."

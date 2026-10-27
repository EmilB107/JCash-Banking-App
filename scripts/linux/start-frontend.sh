#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
FRONTEND_DIR="$ROOT_DIR/frontend"

command -v node >/dev/null 2>&1 || { echo "Node.js is required." >&2; exit 1; }
command -v npm >/dev/null 2>&1 || { echo "npm is required." >&2; exit 1; }
[[ -f "$FRONTEND_DIR/package.json" ]] || { echo "Missing frontend/package.json." >&2; exit 1; }
[[ -f "$FRONTEND_DIR/package-lock.json" ]] || { echo "Missing frontend/package-lock.json." >&2; exit 1; }
if [[ ! -x "$FRONTEND_DIR/node_modules/.bin/ng" ]]; then
  echo "Frontend dependencies are not installed. Run 'npm ci' in frontend/ first." >&2
  exit 1
fi

cd "$FRONTEND_DIR"
echo "Starting Angular frontend with the existing npm start script."
exec npm start

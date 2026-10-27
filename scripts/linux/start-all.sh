#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
LOG_DIR="$(mktemp -d "${TMPDIR:-/tmp}/jcash-dev.XXXXXX")"
backend_pid=""
frontend_pid=""

cleanup() {
  trap - EXIT INT TERM
  [[ -n "$backend_pid" ]] && kill -- "-$backend_pid" 2>/dev/null || true
  [[ -n "$frontend_pid" ]] && kill -- "-$frontend_pid" 2>/dev/null || true
  echo "Stopped processes started by JCash. Logs remain in $LOG_DIR."
}
trap cleanup EXIT INT TERM

"$SCRIPT_DIR/start-db.sh"

if ! command -v setsid >/dev/null 2>&1; then
  echo "setsid is required for safe combined-process cleanup on Linux." >&2
  exit 1
fi

echo "Starting backend; log: $LOG_DIR/backend.log"
setsid "$SCRIPT_DIR/start-backend.sh" >"$LOG_DIR/backend.log" 2>&1 &
backend_pid=$!
echo "Starting frontend; log: $LOG_DIR/frontend.log"
setsid "$SCRIPT_DIR/start-frontend.sh" >"$LOG_DIR/frontend.log" 2>&1 &
frontend_pid=$!

echo "Waiting for backend health endpoint..."
backend_ready=0
if command -v curl >/dev/null 2>&1; then
  for _ in {1..60}; do
    if ! kill -0 "$backend_pid" 2>/dev/null; then break; fi
    if curl --silent --fail --max-time 2 http://localhost:8080/api/health >/dev/null; then
      backend_ready=1
      break
    fi
    sleep 1
  done
fi
if [[ "$backend_ready" -ne 1 ]]; then
  echo "Backend did not become ready. See $LOG_DIR/backend.log." >&2
  exit 1
fi
if ! kill -0 "$frontend_pid" 2>/dev/null; then
  echo "Frontend exited during startup. See $LOG_DIR/frontend.log." >&2
  exit 1
fi

echo "Backend is ready at http://localhost:8080/api/health."
echo "Frontend process is running; Angular normally serves http://localhost:4200."
echo "Press Ctrl+C to stop the backend and frontend started by this script."
wait

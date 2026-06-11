#!/usr/bin/env bash
# =============================================================================
# DEPRECATED — Legacy monolith stop on :8098.
#
# The project has migrated to microservices. Use the following instead:
#
#   docker compose down          # Stop all microservice containers
#   docker compose stop <svc>    # Stop a specific service
# =============================================================================
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

PID="$(lsof -tiTCP:8098 -sTCP:LISTEN 2>/dev/null | head -1 || true)"
if [[ -z "$PID" && -f logs/big-market-app.pid ]]; then
  PID="$(cat logs/big-market-app.pid)"
fi

if [[ -n "$PID" ]] && kill -0 "$PID" 2>/dev/null; then
  kill "$PID"
  echo "Stopped application, pid=$PID"
elif [[ -n "$PID" ]]; then
  echo "Application process is not running, stale pid=$PID"
else
  echo "Application is not running."
fi

screen -S big-market-app -X quit 2>/dev/null || true
rm -f logs/big-market-app.pid

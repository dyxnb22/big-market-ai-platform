#!/usr/bin/env bash
# =============================================================================
# DEPRECATED — Legacy monolith status check on :8098.
#
# The project has migrated to microservices. Use the following instead:
#
#   curl -s http://127.0.0.1:8080/actuator/health       # Gateway health
#   docker compose ps                                     # Container status
# =============================================================================
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

PID="$(lsof -tiTCP:8098 -sTCP:LISTEN 2>/dev/null | head -1 || true)"
if [[ -n "$PID" ]]; then
  echo "$PID" > logs/big-market-app.pid
  echo "Application is running, pid=$PID"
else
  echo "Application is not running."
fi

curl -fsS http://127.0.0.1:8098/actuator/health 2>/dev/null || true
echo

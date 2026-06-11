#!/usr/bin/env bash
# =============================================================================
# DEPRECATED — This script starts the legacy single-JVM big-market-app on :8098.
#
# The project has migrated to a microservices architecture behind the gateway.
# Use the following commands instead:
#
#   docker compose up --build -d          # Start all backend services + gateway
#   ./scripts/web-start.sh                 # Start frontend on :5173
#
# The gateway runs on :8080 and routes to 7+ backend services.
# =============================================================================
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

mkdir -p logs

RUNNING_PID="$(lsof -tiTCP:8098 -sTCP:LISTEN 2>/dev/null | head -1 || true)"
if [[ -n "$RUNNING_PID" ]]; then
  echo "$RUNNING_PID" > logs/big-market-app.pid
  echo "Application is already running, pid=$RUNNING_PID"
  exit 0
fi

rm -f logs/big-market-app.pid
screen -S big-market-app -dm bash -lc 'cd /Users/diaoyuxuan/big-market-ai-platform; exec java -jar big-market-app/target/big-market-app.jar > logs/big-market-app.log 2>&1'

for _ in {1..120}; do
  RUNNING_PID="$(lsof -tiTCP:8098 -sTCP:LISTEN 2>/dev/null | head -1 || true)"
  if [[ -n "$RUNNING_PID" ]]; then
    echo "$RUNNING_PID" > logs/big-market-app.pid
    break
  fi
  sleep 0.1
done

echo "Application starting, pid=$(cat logs/big-market-app.pid 2>/dev/null || true)"
echo "Log: $ROOT_DIR/logs/big-market-app.log"

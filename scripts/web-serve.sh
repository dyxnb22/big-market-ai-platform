#!/usr/bin/env bash
# Start big-market-web static server (no screen/tmux required).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WEB_PORT="${WEB_PORT:-5173}"
PID_FILE="$ROOT/logs/big-market-web.pid"
LOG_FILE="$ROOT/logs/big-market-web.log"

mkdir -p "$ROOT/logs"

if [[ -f "$PID_FILE" ]] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
  if curl -fsS "http://127.0.0.1:${WEB_PORT}/" >/dev/null 2>&1; then
    echo "Web already running: http://127.0.0.1:${WEB_PORT}"
    exit 0
  fi
fi

pkill -f "[p]ython3 .*big-market-web/server.py ${WEB_PORT}" 2>/dev/null || true
sleep 0.2

nohup python3 "$ROOT/big-market-web/server.py" "$WEB_PORT" >"$LOG_FILE" 2>&1 &
echo $! >"$PID_FILE"

for _ in $(seq 1 30); do
  if curl -fsS "http://127.0.0.1:${WEB_PORT}/" >/dev/null 2>&1; then
    echo "Web ready: http://127.0.0.1:${WEB_PORT}"
    exit 0
  fi
  sleep 0.2
done

echo "Web failed to start; see $LOG_FILE" >&2
exit 1

#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

mkdir -p logs

if [[ -f logs/big-market-web.pid ]] && kill -0 "$(cat logs/big-market-web.pid)" 2>/dev/null; then
  echo "Web is already running: http://127.0.0.1:5173"
  exit 0
fi

screen -S big-market-web -X quit >/dev/null 2>&1 || true
screen -dmS big-market-web bash -lc 'cd "$1/big-market-web" && echo $$ > "$1/logs/big-market-web.pid" && exec python3 server.py 5173 > "$1/logs/big-market-web.log" 2>&1' bash "$ROOT_DIR"

for _ in $(seq 1 20); do
  if curl -fsS http://127.0.0.1:5173 >/dev/null 2>&1; then
    echo "Web is running: http://127.0.0.1:5173"
    echo "Log: $ROOT_DIR/logs/big-market-web.log"
    exit 0
  fi
  sleep 0.2
done

echo "Web starting: http://127.0.0.1:5173"
echo "Log: $ROOT_DIR/logs/big-market-web.log"

#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

screen -S big-market-web -X quit >/dev/null 2>&1 || true

if [[ -f logs/big-market-web.pid ]]; then
  PID="$(cat logs/big-market-web.pid)"
  if kill -0 "$PID" 2>/dev/null; then
    kill "$PID" 2>/dev/null || true
  fi
  rm -f logs/big-market-web.pid
fi

echo "Web stopped."

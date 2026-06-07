#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

if curl -fsS http://127.0.0.1:5173 >/dev/null 2>&1; then
  echo "Web is running: http://127.0.0.1:5173"
else
  echo "Web is not running."
fi

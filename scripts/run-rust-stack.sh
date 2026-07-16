#!/usr/bin/env bash
# Start Rust gateway + app (memory backend) in background.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
RS="$ROOT/big-market-rs"
cd "$RS"

cargo build -q --release -p bm-app -p bm-gateway

pkill -f '[t]arget/release/bm-app' 2>/dev/null || true
pkill -f '[t]arget/release/bm-gateway' 2>/dev/null || true
sleep 0.3

export BM_PORT="${BM_PORT:-8083}"
export BM_GW_PORT="${BM_GW_PORT:-8080}"
export BM_GW_APP_URL="${BM_GW_APP_URL:-http://127.0.0.1:${BM_PORT}}"
export BM_EMBED_WORKER="${BM_EMBED_WORKER:-1}"
export RUST_LOG="${RUST_LOG:-info}"

mkdir -p "$RS/target/run"
nohup "$RS/target/release/bm-app" >"$RS/target/run/bm-app.log" 2>&1 &
echo $! >"$RS/target/run/bm-app.pid"
nohup "$RS/target/release/bm-gateway" >"$RS/target/run/bm-gateway.log" 2>&1 &
echo $! >"$RS/target/run/bm-gateway.pid"

for i in $(seq 1 50); do
  if curl -fsS "http://127.0.0.1:${BM_GW_PORT}/health" >/dev/null 2>&1; then
    echo "Rust stack ready on :${BM_GW_PORT} (app :${BM_PORT})"
    exit 0
  fi
  sleep 0.2
done
echo "stack failed to become ready; see $RS/target/run/*.log" >&2
exit 1

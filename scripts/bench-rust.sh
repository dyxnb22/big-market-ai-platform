#!/usr/bin/env bash
# Lightweight Rust stack bench (RSS + cold ready). No Java/Docker required.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
RS="$ROOT/big-market-rs"
DATA_DIR="$RS/target/bench-data"
export BM_DATA_DIR="$DATA_DIR"
export BM_BACKEND=file

pkill -f '[t]arget/release/bm-app' 2>/dev/null || true
pkill -f '[t]arget/release/bm-gateway' 2>/dev/null || true
sleep 0.3
rm -rf "$DATA_DIR"
mkdir -p "$DATA_DIR"

(cd "$RS" && cargo build -q --release -p bm-app -p bm-gateway)

start_epoch=$(date +%s%3N)
nohup "$RS/target/release/bm-app" >"$RS/target/bench-app.log" 2>&1 &
APP_PID=$!
nohup "$RS/target/release/bm-gateway" >"$RS/target/bench-gw.log" 2>&1 &
GW_PID=$!

ready_ms=0
for _ in $(seq 1 50); do
  if curl -fsS http://127.0.0.1:8080/health >/dev/null 2>&1; then
    ready_ms=$(( $(date +%s%3N) - start_epoch ))
    break
  fi
  sleep 0.1
done

rss_kb() {
  local pid=$1
  if [[ -r "/proc/$pid/status" ]]; then
    awk '/VmRSS:/ {print $2}' "/proc/$pid/status"
  else
    echo "n/a"
  fi
}

app_rss=$(rss_kb "$APP_PID")
gw_rss=$(rss_kb "$GW_PID")
total_rss=$(( app_rss + gw_rss ))

echo "=== Rust bench ($(date -Iseconds)) ==="
echo "cold_ready_ms: ${ready_ms}"
echo "bm-app RSS KiB: ${app_rss} (pid ${APP_PID})"
echo "bm-gateway RSS KiB: ${gw_rss} (pid ${GW_PID})"
echo "combined RSS KiB: ${total_rss}"
echo "combined RSS MiB: $(( total_rss / 1024 ))"

kill "$APP_PID" "$GW_PID" 2>/dev/null || true

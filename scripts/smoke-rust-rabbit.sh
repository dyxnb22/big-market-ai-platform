#!/usr/bin/env bash
# Smoke Rust award path via RabbitMQ bridge (skips when Rabbit unavailable).
# Topology: bm-app with BM_EMBED_WORKER=0 + standalone bm-worker + BM_RABBIT_URL.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
RS="$ROOT/big-market-rs"
API="${API:-http://127.0.0.1:8080/api/v1}"
RABBIT_URL="${BM_RABBIT_URL:-amqp://admin:admin@127.0.0.1:5672/%2F}"
WORKER_PORT="${BM_WORKER_PORT:-8085}"

pass() { echo "  PASS  $*"; }
fail() { echo "  FAIL  $*" >&2; exit 1; }

json_field() {
  python3 -c "import json,sys; d=json.load(sys.stdin); print($1)"
}

# Probe Rabbit TCP
RABBIT_HOST="${RABBIT_HOST:-127.0.0.1}"
RABBIT_PORT="${RABBIT_PORT:-5672}"
if ! (echo >/dev/tcp/"$RABBIT_HOST"/"$RABBIT_PORT") >/dev/null 2>&1; then
  echo "SKIP  smoke-rust-rabbit: RabbitMQ not reachable at ${RABBIT_HOST}:${RABBIT_PORT}"
  exit 0
fi

echo "=== Rust Rabbit smoke (embed off + bm-worker) ==="

cd "$RS"
cargo build -q --release -p bm-app -p bm-gateway -p bm-worker

pkill -f '[t]arget/release/bm-app' 2>/dev/null || true
pkill -f '[t]arget/release/bm-gateway' 2>/dev/null || true
pkill -f '[t]arget/release/bm-worker' 2>/dev/null || true
sleep 0.4

export BM_PORT="${BM_PORT:-8083}"
export BM_GW_PORT="${BM_GW_PORT:-8080}"
export BM_GW_APP_URL="http://127.0.0.1:${BM_PORT}"
export BM_EMBED_WORKER=0
export BM_BACKEND="${BM_BACKEND:-file}"
export BM_DATA_DIR="${BM_DATA_DIR:-$RS/target/run/rabbit-data}"
export BM_RABBIT_URL="$RABBIT_URL"
export BM_WORKER_PORT="$WORKER_PORT"
export BM_WORKER_POLL_SECS="${BM_WORKER_POLL_SECS:-1}"
export RUST_LOG="${RUST_LOG:-info}"
mkdir -p "$BM_DATA_DIR" "$RS/target/run"

nohup "$RS/target/release/bm-app" >"$RS/target/run/bm-app-rabbit.log" 2>&1 &
echo $! >"$RS/target/run/bm-app.pid"
nohup "$RS/target/release/bm-gateway" >"$RS/target/run/bm-gateway-rabbit.log" 2>&1 &
echo $! >"$RS/target/run/bm-gateway.pid"
nohup "$RS/target/release/bm-worker" >"$RS/target/run/bm-worker-rabbit.log" 2>&1 &
echo $! >"$RS/target/run/bm-worker.pid"

for i in $(seq 1 60); do
  if curl -fsS "http://127.0.0.1:${BM_GW_PORT}/health" >/dev/null 2>&1 \
    && curl -fsS "http://127.0.0.1:${WORKER_PORT}/health" >/dev/null 2>&1; then
    break
  fi
  sleep 0.2
done
curl -fsS "http://127.0.0.1:${BM_GW_PORT}/health" >/dev/null || fail "gateway not ready"
curl -fsS "http://127.0.0.1:${WORKER_PORT}/health" >/dev/null || fail "worker not ready"
pass "stack ready (embed=0)"

JOBS="$(curl -fsS "http://127.0.0.1:${WORKER_PORT}/actuator/jobs")"
echo "$JOBS" | grep -q consume_send_award || fail "jobs catalog: $JOBS"
pass "actuator/jobs"

LOGIN="$(curl -fsS "$API/auth/login" -H 'Content-Type: application/json' \
  -d '{"userId":"xiaofuge","password":"demo"}')"
TOKEN="$(printf '%s' "$LOGIN" | json_field "d['data']['token']")"
ADMIN_LOGIN="$(curl -fsS "$API/auth/login" -H 'Content-Type: application/json' \
  -d '{"userId":"admin","password":"admin"}')"
ADMIN_TOKEN="$(printf '%s' "$ADMIN_LOGIN" | json_field "d['data']['token']")"
curl -fsS "$API/raffle/activity/armory?activityId=100401" \
  -H "Authorization: Bearer $ADMIN_TOKEN" >/dev/null

BEFORE="$(curl -fsS "$API/raffle/activity/query_user_credit_account_by_token" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{}')"
BAL0="$(printf '%s' "$BEFORE" | json_field "d['data']")"

REQ="rabbit-smoke-$(date +%s)-$$"
curl -fsS -X POST "$API/raffle/activity/credit_pay_exchange_sku_by_token" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d "{\"sku\":9901,\"requestId\":\"$REQ\"}" >/dev/null

DRAW="$(curl -fsS -X POST "$API/raffle/activity/draw_by_token" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"activityId":100401}')"
[ "$(printf '%s' "$DRAW" | json_field "d['data']['awardId']")" = "101" ] || fail "draw: $DRAW"
pass "draw"

FINAL=""
for _ in $(seq 1 40); do
  FINAL="$(curl -fsS "$API/raffle/activity/query_user_credit_account_by_token" \
    -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{}' \
    | json_field "d['data']")"
  if python3 -c "from decimal import Decimal; import sys; sys.exit(0 if Decimal('$BAL0')==Decimal('$FINAL') else 1)"; then
    break
  fi
  sleep 0.25
done
python3 -c "from decimal import Decimal; assert Decimal('$BAL0')==Decimal('$FINAL'), ('$BAL0','$FINAL')"
pass "award credited via worker (+ Rabbit when connected)"

echo
echo "Rust Rabbit smoke passed."

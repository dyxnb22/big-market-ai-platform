#!/usr/bin/env bash
# Microservices smoke test — validates the local microservices stack.
#
# Usage: ./scripts/smoke-test-microservices.sh [gateway-host]
# Default host: localhost
#
# Expected result: 21/21 PASS
#   - 8 health checks  (gateway + 7 backend services)
#   - 11 functional API checks (incl. HTTP+code for unauth paths)
#   - 2 gateway fallback checks (HTTP 503 + body code 0007)

set -euo pipefail

HOST="${1:-localhost}"
GW="http://$HOST:8080"
AUTH="http://$HOST:8081"
CHANNEL="${CHANNEL:-c01}"
SOURCE="${SOURCE:-s01}"

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck source=lib/health-poll.sh
source "$ROOT/scripts/lib/health-poll.sh"

PASS=0
FAIL=0

check() {
  local label="$1" expected="$2" actual="$3"
  if echo "$actual" | grep -q "\"$expected\""; then
    echo "  PASS  $label"
    PASS=$((PASS+1))
  else
    echo "  FAIL  $label  (expected code=$expected, got: ${actual:0:120})"
    FAIL=$((FAIL+1))
  fi
}

echo "=== Microservices Smoke Test ==="
echo ""

echo "--- Health checks ---"
for svc_port in "auth-service:$HOST:8081" "admin-service:$HOST:8082" "market-service:$HOST:8083" "chatbot-service:$HOST:8084" "gateway:$HOST:8080" "message-job-service:$HOST:8085" "account-service:$HOST:8086" "fulfillment-service:$HOST:8087"; do
  name="${svc_port%%:*}"; addr="${svc_port#*:}"
  result=$(curl -sf "http://$addr/actuator/health" | python3 -c "import sys,json; print(json.load(sys.stdin)['status'])" 2>/dev/null || echo "UNREACHABLE")
  if [ "$result" = "UP" ]; then
    echo "  PASS  $name health: UP"
    PASS=$((PASS+1))
  else
    echo "  FAIL  $name health: $result"
    FAIL=$((FAIL+1))
  fi
done

ACTIVITY_ID="$(resolve_stage_activity_id "$GW" "$CHANNEL" "$SOURCE" || true)"
if [ -z "${ACTIVITY_ID:-}" ]; then
  echo "  FAIL  resolve stage activityId (channel=${CHANNEL} source=${SOURCE})"
  FAIL=$((FAIL+1))
  ACTIVITY_ID="0"
else
  echo "  Using activityId=${ACTIVITY_ID} (channel=${CHANNEL} source=${SOURCE})"
fi

echo ""
echo "--- Auth service (direct) ---"
LOGIN=$(curl -sf -X POST "$AUTH/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"userId":"xiaofuge","password":"demo"}' 2>/dev/null || echo '{"code":"FAIL"}')
check "auth/login (direct)" "0000" "$LOGIN"
TOKEN=$(echo "$LOGIN" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('data',{}).get('token',''))" 2>/dev/null || echo "")

VERIFY=$(curl -sf "$AUTH/api/v1/auth/verify" -H "Authorization: $TOKEN" 2>/dev/null || echo '{"code":"FAIL"}')
check "auth/verify (direct, raw token)" "0000" "$VERIFY"

echo ""
echo "--- Gateway routing ---"
GW_LOGIN=$(curl -sf -X POST "$GW/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"userId":"xiaofuge","password":"demo"}' 2>/dev/null || echo '{"code":"FAIL"}')
check "gateway → auth/login" "0000" "$GW_LOGIN"
GW_TOKEN=$(echo "$GW_LOGIN" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('data',{}).get('token',''))" 2>/dev/null || echo "")

GW_VERIFY=$(curl -sf "$GW/api/v1/auth/verify" -H "Authorization: $GW_TOKEN" 2>/dev/null || echo '{"code":"FAIL"}')
check "gateway → auth/verify" "0000" "$GW_VERIFY"

GW_ADMIN_NO_AUTH_BODY=$(mktemp)
GW_ADMIN_NO_AUTH_HTTP=$(curl -sS -o "$GW_ADMIN_NO_AUTH_BODY" -w '%{http_code}' "$GW/api/v1/admin/config/list" 2>/dev/null || echo "000")
GW_ADMIN_NO_AUTH=$(cat "$GW_ADMIN_NO_AUTH_BODY")
rm -f "$GW_ADMIN_NO_AUTH_BODY"
if [ "$GW_ADMIN_NO_AUTH_HTTP" = "401" ]; then
  echo "  PASS  gateway → admin/config/list (no auth) HTTP 401"
  PASS=$((PASS+1))
else
  echo "  FAIL  gateway → admin/config/list (no auth) HTTP (expected 401, got $GW_ADMIN_NO_AUTH_HTTP)"
  FAIL=$((FAIL+1))
fi
check "gateway → admin/config/list (no auth, expect 0009)" "0009" "$GW_ADMIN_NO_AUTH"

GW_ADMIN_LOGIN=$(curl -sf -X POST "$GW/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"userId":"admin","password":"admin"}' 2>/dev/null || echo '{"code":"FAIL"}')
GW_ADMIN_TOKEN=$(echo "$GW_ADMIN_LOGIN" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('data',{}).get('token',''))" 2>/dev/null || echo "")

GW_ADMIN=$(curl -sf "$GW/api/v1/admin/config/list" \
  -H "Authorization: $GW_ADMIN_TOKEN" 2>/dev/null || echo '{"code":"FAIL"}')
check "gateway → admin/config/list (with auth)" "0000" "$GW_ADMIN"

GW_CHATBOT='{"code":"FAIL"}'
for attempt in 1 2 3; do
  GW_CHATBOT=$(curl -sS -X POST "$GW/api/v1/chatbot/ask" \
    -H "Content-Type: application/json" \
    -H "Authorization: $GW_TOKEN" \
    -d "{\"requestId\":\"smoke-chat-$(date +%s)-${attempt}\",\"activityId\":${ACTIVITY_ID},\"message\":\"hello smoke test\"}" 2>/dev/null || echo '{"code":"FAIL"}')
  if echo "$GW_CHATBOT" | grep -q '"0000"'; then
    break
  fi
  sleep 2
done
check "gateway → chatbot/ask (with auth, credit charged)" "0000" "$GW_CHATBOT"

GW_MARKET=$(curl -sf "$GW/api/v1/raffle/activity/query_stage_activity_id?channel=${CHANNEL}&source=${SOURCE}" 2>/dev/null || echo '{"code":"FAIL"}')
check "gateway → market/query_stage_activity_id" "0000" "$GW_MARKET"

GW_DRAW_BODY=$(mktemp)
GW_DRAW_HTTP=$(curl -sS -o "$GW_DRAW_BODY" -w '%{http_code}' -X POST "$GW/api/v1/raffle/activity/draw_by_token" \
  -H "Content-Type: application/json" \
  -d "{\"activityId\":${ACTIVITY_ID}}" 2>/dev/null || echo "000")
GW_DRAW_NO_TOKEN=$(cat "$GW_DRAW_BODY")
rm -f "$GW_DRAW_BODY"
if [ "$GW_DRAW_HTTP" = "401" ]; then
  echo "  PASS  gateway → market/draw_by_token (no token) HTTP 401"
  PASS=$((PASS+1))
else
  echo "  FAIL  gateway → market/draw_by_token (no token) HTTP (expected 401, got $GW_DRAW_HTTP)"
  FAIL=$((FAIL+1))
fi
check "gateway → market/draw_by_token (no token, expect 0009)" "0009" "$GW_DRAW_NO_TOKEN"

echo ""
echo "--- Gateway fallback endpoint ---"
FALLBACK_BODY=$(mktemp)
GW_FALLBACK_HTTP=$(curl -sS -o "$FALLBACK_BODY" -w '%{http_code}' "$GW/fallback/auth-service" 2>/dev/null || echo "000")
GW_FALLBACK=$(cat "$FALLBACK_BODY")
rm -f "$FALLBACK_BODY"
if [ "$GW_FALLBACK_HTTP" = "503" ]; then
  echo "  PASS  gateway fallback HTTP status 503"
  PASS=$((PASS+1))
else
  echo "  FAIL  gateway fallback HTTP status (expected 503, got $GW_FALLBACK_HTTP)"
  FAIL=$((FAIL+1))
fi
check "gateway fallback endpoint returns 0007" "0007" "$GW_FALLBACK"

echo ""
echo "=========================================="
echo "Results: $PASS passed, $FAIL failed  (expected 21/21)"
echo "=========================================="
[ "$FAIL" -eq 0 ] && exit 0 || exit 1

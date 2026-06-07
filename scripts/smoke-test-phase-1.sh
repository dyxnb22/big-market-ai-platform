#!/usr/bin/env bash
# Phase 1 microservices smoke test.
# Usage: ./scripts/smoke-test-phase-1.sh [gateway-host]
# Default host: localhost

set -euo pipefail

HOST="${1:-localhost}"
GW="http://$HOST:8080"
AUTH="http://$HOST:8081"
ADMIN="http://$HOST:8082"
MARKET="http://$HOST:8083"
CHATBOT="http://$HOST:8084"
ADMIN_TOKEN="${ADMIN_TOKEN:-admin-dev-token}"

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

echo "=== Phase 1 Smoke Test ==="
echo ""

echo "--- Health checks ---"
for svc_port in "auth-service:$HOST:8081" "admin-service:$HOST:8082" "market-service:$HOST:8083" "chatbot-service:$HOST:8084" "gateway:$HOST:8080"; do
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

echo ""
echo "--- Auth service (direct) ---"
LOGIN=$(curl -sf -X POST "$AUTH/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"userId":"smoke-test-user"}' 2>/dev/null || echo '{"code":"FAIL"}')
check "auth/login (direct)" "0000" "$LOGIN"
TOKEN=$(echo "$LOGIN" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('data',{}).get('token',''))" 2>/dev/null || echo "")

VERIFY=$(curl -sf "$AUTH/api/v1/auth/verify" -H "Authorization: $TOKEN" 2>/dev/null || echo '{"code":"FAIL"}')
check "auth/verify (direct, raw token)" "0000" "$VERIFY"

echo ""
echo "--- Gateway routing ---"
GW_LOGIN=$(curl -sf -X POST "$GW/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"userId":"smoke-test-user"}' 2>/dev/null || echo '{"code":"FAIL"}')
check "gateway → auth/login" "0000" "$GW_LOGIN"
GW_TOKEN=$(echo "$GW_LOGIN" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('data',{}).get('token',''))" 2>/dev/null || echo "")

GW_VERIFY=$(curl -sf "$GW/api/v1/auth/verify" -H "Authorization: $GW_TOKEN" 2>/dev/null || echo '{"code":"FAIL"}')
check "gateway → auth/verify" "0000" "$GW_VERIFY"

GW_ADMIN_NO_AUTH=$(curl -sf "$GW/api/v1/admin/config/list" 2>/dev/null || echo '{"code":"FAIL"}')
check "gateway → admin/config/list (no auth, expect 0009)" "0009" "$GW_ADMIN_NO_AUTH"

GW_ADMIN=$(curl -sf "$GW/api/v1/admin/config/list" \
  -H "Authorization: $GW_TOKEN" \
  -H "Admin-Token: $ADMIN_TOKEN" 2>/dev/null || echo '{"code":"FAIL"}')
check "gateway → admin/config/list (with auth)" "0000" "$GW_ADMIN"

GW_CHATBOT=$(curl -sf -X POST "$GW/api/v1/chatbot/ask" \
  -H "Content-Type: application/json" \
  -d '{"message":"hello smoke test"}' 2>/dev/null || echo '{"code":"FAIL"}')
check "gateway → chatbot/ask" "0000" "$GW_CHATBOT"

GW_MARKET=$(curl -sf "$GW/api/v1/raffle/activity/query_stage_activity_id?channel=default&source=web" 2>/dev/null || echo '{"code":"FAIL"}')
check "gateway → market/query_stage_activity_id" "0000" "$GW_MARKET"

GW_DRAW_NO_TOKEN=$(curl -sf -X POST "$GW/api/v1/raffle/activity/draw_by_token" \
  -H "Content-Type: application/json" \
  -d '{"activityId":100301}' 2>/dev/null || echo '{"code":"FAIL"}')
check "gateway → market/draw_by_token (no token, expect 0009)" "0009" "$GW_DRAW_NO_TOKEN"

echo ""
echo "=========================================="
echo "Results: $PASS passed, $FAIL failed"
echo "=========================================="
[ "$FAIL" -eq 0 ] && exit 0 || exit 1

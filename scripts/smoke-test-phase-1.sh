#!/usr/bin/env bash
# Microservices smoke test — validates the full 7-service stack (Phase 2.2-A dark launch).
#
# Historical note: this script is named smoke-test-phase-1 for backwards compatibility
# (it was introduced in Phase 1) but it now covers all 7 services including
# big-market-message-job-service (added in Phase 2.1) and
# big-market-account-service (dark-launched in Phase 2.2-A, Dubbo/internal only).
# The canonical alias is:
#   ./scripts/validate-microservices-stack.sh  ← orchestrates build + docker + this script
#
# Usage: ./scripts/smoke-test-phase-1.sh [gateway-host]
# Default host: localhost
#
# Expected result: 17/17 PASS
#   - 7 health checks  (gateway + 6 backend services, including account-service dark launch)
#   - 9 functional API checks
#   - 1 gateway fallback endpoint check

set -euo pipefail

HOST="${1:-localhost}"
GW="http://$HOST:8080"
AUTH="http://$HOST:8081"
ADMIN="http://$HOST:8082"
MARKET="http://$HOST:8083"
CHATBOT="http://$HOST:8084"
MSGJ="http://$HOST:8085"
ACCOUNT="http://$HOST:8086"
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

echo "=== Phase 2.2-A Smoke Test (7-service dark launch) ==="
echo ""

echo "--- Health checks ---"
for svc_port in "auth-service:$HOST:8081" "admin-service:$HOST:8082" "market-service:$HOST:8083" "chatbot-service:$HOST:8084" "gateway:$HOST:8080" "message-job-service:$HOST:8085" "account-service(dark):$HOST:8086"; do
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
echo "--- Gateway fallback endpoint ---"
GW_FALLBACK=$(curl -sf "$GW/fallback/auth-service" 2>/dev/null || echo '{"code":"FAIL"}')
check "gateway fallback endpoint returns 0007" "0007" "$GW_FALLBACK"

echo ""
echo "=========================================="
echo "Results: $PASS passed, $FAIL failed  (expected 17/17)"
echo "=========================================="
[ "$FAIL" -eq 0 ] && exit 0 || exit 1

#!/usr/bin/env bash
# API smoke with exact business-code assertions. Fail-closed.
set -euo pipefail

API="${API:-http://127.0.0.1:8080/api/v1}"
BASE_URL="${API%/api/v1}"
CHANNEL="${CHANNEL:-c01}"
SOURCE="${SOURCE:-s01}"
DEMO_USER_ID="${DEMO_USER_ID:-xiaofuge}"
DEMO_USER_PASSWORD="${DEMO_USER_PASSWORD:-demo}"
DEMO_ADMIN_USER_ID="${DEMO_ADMIN_USER_ID:-admin}"
DEMO_ADMIN_PASSWORD="${DEMO_ADMIN_PASSWORD:-admin}"

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck source=lib/health-poll.sh
source "$ROOT/scripts/lib/health-poll.sh"

echo "=== API Smoke (strict) ==="
echo

HEALTH="$(curl -fsS "$BASE_URL/actuator/health")"
STATUS="$(printf '%s' "$HEALTH" | python3 -c "import sys,json; print(json.load(sys.stdin).get('status',''))")"
if [ "$STATUS" != "UP" ]; then
  echo "  FAIL  actuator health (got: ${HEALTH:0:120})" >&2
  exit 1
fi
echo "  PASS  actuator health UP"

LOGIN_RESPONSE="$(curl -fsS "$API/auth/login" \
  -H 'Content-Type: application/json' \
  -d "{\"userId\":\"$DEMO_USER_ID\",\"password\":\"$DEMO_USER_PASSWORD\"}")"
assert_json_code "auth/login" "0000" "$LOGIN_RESPONSE"
TOKEN="$(printf '%s' "$LOGIN_RESPONSE" | python3 -c "import sys,json; print(json.load(sys.stdin).get('data',{}).get('token',''))")"
test -n "$TOKEN"

ADMIN_LOGIN_RESPONSE="$(curl -fsS "$API/auth/login" \
  -H 'Content-Type: application/json' \
  -d "{\"userId\":\"$DEMO_ADMIN_USER_ID\",\"password\":\"$DEMO_ADMIN_PASSWORD\"}")"
assert_json_code "auth/login admin" "0000" "$ADMIN_LOGIN_RESPONSE"
ADMIN_TOKEN="$(printf '%s' "$ADMIN_LOGIN_RESPONSE" | python3 -c "import sys,json; print(json.load(sys.stdin).get('data',{}).get('token',''))")"
test -n "$ADMIN_TOKEN"

VERIFY="$(curl -fsS "$API/auth/verify" -H "Authorization: $TOKEN")"
assert_json_code "auth/verify" "0000" "$VERIFY"

ACTIVITY_ID="$(resolve_stage_activity_id "$BASE_URL" "$CHANNEL" "$SOURCE")"
echo "  Using activityId=${ACTIVITY_ID}"

ARMORY="$(curl -fsS -H "X-Admin-Token: ${ADMIN_DEV_TOKEN:-admin-dev-token}" \
  "$API/raffle/activity/armory?activityId=${ACTIVITY_ID}")"
assert_json_code "activity armory" "0000" "$ARMORY"

STRATEGY_ARMORY="$(curl -fsS -H "X-Admin-Token: ${ADMIN_DEV_TOKEN:-admin-dev-token}" \
  "$API/raffle/strategy/strategy_armory?strategyId=100006")"
assert_json_code "strategy armory" "0000" "$STRATEGY_ARMORY"

ACCOUNT="$(curl -fsS "$API/raffle/activity/query_user_activity_account_by_token" \
  -H "Authorization: $TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{\"activityId\":${ACTIVITY_ID}}")"
assert_json_code "user activity account" "0000" "$ACCOUNT"

AWARD_RECORDS="$(curl -fsS "$API/raffle/activity/query_user_award_record_by_token" \
  -H "Authorization: $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{}')"
assert_json_code "user award records" "0000" "$AWARD_RECORDS"

CREDIT_ORDERS="$(curl -fsS "$API/raffle/activity/query_user_credit_order_by_token" \
  -H "Authorization: $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{}')"
assert_json_code "user credit orders" "0000" "$CREDIT_ORDERS"

ADMIN_LIST="$(curl -fsS "$API/admin/config/list" -H "Authorization: $ADMIN_TOKEN")"
assert_json_code "admin config list" "0000" "$ADMIN_LIST"

CHAT="$(curl -fsS "$API/chatbot/ask" \
  -H "Authorization: $TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{\"requestId\":\"smoke-$(date +%s)\",\"activityId\":${ACTIVITY_ID},\"message\":\"smoke api check\"}")"
assert_json_code "chatbot ask" "0000" "$CHAT"

echo
echo "=== API Smoke ALL PASSED ==="

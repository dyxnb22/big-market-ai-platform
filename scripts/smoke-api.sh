#!/usr/bin/env bash
# API smoke (file/memory stack) — auth, exchange, draw, credit round-trip.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
API="${API:-http://127.0.0.1:8080/api/v1}"

pass() { echo "  PASS  $*"; }
fail() { echo "  FAIL  $*" >&2; exit 1; }

json_field() {
  python3 -c "import json,sys; d=json.load(sys.stdin); print($1)"
}

# Always (re)start so smoke hits the binary just built by acceptance/clippy.
"$ROOT/scripts/run-stack.sh"

echo "=== API smoke ==="

LOGIN="$(curl -fsS "$API/auth/login" -H 'Content-Type: application/json' \
  -d '{"userId":"xiaofuge","password":"demo"}')"
TOKEN="$(printf '%s' "$LOGIN" | json_field "d['data']['token']")"
[ -n "$TOKEN" ] || fail "login: $LOGIN"
pass "login"

VERIFY="$(curl -fsS "$API/auth/verify" -H "Authorization: Bearer $TOKEN")"
[ "$(printf '%s' "$VERIFY" | json_field "d['data']")" = "xiaofuge" ] || fail "verify: $VERIFY"
pass "verify"

BAD="$(curl -fsS "$API/auth/login" -H 'Content-Type: application/json' \
  -d '{"userId":"xiaofuge","password":"wrong"}' || true)"
[ "$(printf '%s' "$BAD" | json_field "d['code']")" = "0009" ] || fail "bad login: $BAD"
pass "bad credentials → 0009"

ACTIVITY="$(curl -fsS "$API/raffle/activity/query_stage_activity_id?channel=c01&source=s01")"
[ "$(printf '%s' "$ACTIVITY" | json_field "d['data']")" = "100401" ] || fail "stage: $ACTIVITY"
pass "stage activity 100401"

ADMIN_LOGIN="$(curl -fsS "$API/auth/login" -H 'Content-Type: application/json' \
  -d '{"userId":"admin","password":"admin"}')"
ADMIN_TOKEN="$(printf '%s' "$ADMIN_LOGIN" | json_field "d['data']['token']")"
ARMORY="$(curl -fsS "$API/raffle/activity/armory?activityId=100401" \
  -H "Authorization: Bearer $ADMIN_TOKEN")"
[ "$(printf '%s' "$ARMORY" | json_field "d['code']")" = "0000" ] || fail "armory: $ARMORY"
pass "armory"

BEFORE="$(curl -fsS "$API/raffle/activity/query_user_credit_account_by_token" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{}')"
BAL0="$(printf '%s' "$BEFORE" | json_field "d['data']")"

REQ="rust-smoke-$(date +%s)-$$"
EX="$(curl -fsS -X POST "$API/raffle/activity/credit_pay_exchange_sku_by_token" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d "{\"sku\":9901,\"requestId\":\"$REQ\"}")"
[ "$(printf '%s' "$EX" | json_field "d['code']")" = "0000" ] || fail "exchange: $EX"
pass "sku exchange"

AFTER_EX="$(curl -fsS "$API/raffle/activity/query_user_credit_account_by_token" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{}')"
BAL1="$(printf '%s' "$AFTER_EX" | json_field "d['data']")"
python3 - "$BAL0" "$BAL1" <<'PY'
from decimal import Decimal
import sys
b0, b1 = map(Decimal, sys.argv[1:])
assert b0 - b1 == Decimal("5.00"), (b0, b1)
PY
pass "exchange deducted 5"

# Same requestId must not double-debit.
EX_REPLAY="$(curl -fsS -X POST "$API/raffle/activity/credit_pay_exchange_sku_by_token" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d "{\"sku\":9901,\"requestId\":\"$REQ\"}")"
[ "$(printf '%s' "$EX_REPLAY" | json_field "d['code']")" = "0000" ] || fail "exchange replay: $EX_REPLAY"
AFTER_REPLAY="$(curl -fsS "$API/raffle/activity/query_user_credit_account_by_token" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{}')"
BAL_REPLAY="$(printf '%s' "$AFTER_REPLAY" | json_field "d['data']")"
[ "$BAL_REPLAY" = "$BAL1" ] || fail "exchange replay changed balance: $BAL1 → $BAL_REPLAY"
pass "sku exchange idempotent replay"

DRAW="$(curl -fsS -X POST "$API/raffle/activity/draw_by_token" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"activityId":100401}')"
[ "$(printf '%s' "$DRAW" | json_field "d['data']['awardId']")" = "101" ] || fail "draw: $DRAW"
ORDER_ID="$(printf '%s' "$DRAW" | json_field "d['data']['orderId']")"
[ -n "$ORDER_ID" ] || fail "draw missing orderId: $DRAW"
[ "$(printf '%s' "$DRAW" | json_field "len(d['data']['orderId'])")" = "12" ] || fail "orderId len: $DRAW"
POOL_BEFORE="$(printf '%s' "$DRAW" | json_field "d['data']['strategyTrace']['poolBefore']")"
[ "$POOL_BEFORE" = "1" ] || fail "strategyTrace: $DRAW"
pass "draw award 101"

# wait for embedded worker to credit
FINAL=""
for _ in $(seq 1 30); do
  FINAL="$(curl -fsS "$API/raffle/activity/query_user_credit_account_by_token" \
    -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{}' \
    | json_field "d['data']")"
  if python3 -c "from decimal import Decimal; import sys; sys.exit(0 if Decimal('$BAL0')==Decimal('$FINAL') else 1)"; then
    break
  fi
  sleep 0.2
done
python3 -c "from decimal import Decimal; assert Decimal('$BAL0')==Decimal('$FINAL'), ('$BAL0','$FINAL')"
pass "award credit restored balance (outbox dispatched)"

TASK="$(curl -fsS -X POST "$API/raffle/activity/query_credit_award_task_by_token" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d "{\"awardOrderId\":\"$ORDER_ID\"}")"
[ "$(printf '%s' "$TASK" | json_field "d['code']")" = "0000" ] || fail "credit task: $TASK"
[ "$(printf '%s' "$TASK" | json_field "d['data']['state']")" = "dispatched" ] || fail "credit task state: $TASK"
pass "credit award task dispatched by orderId"

MISSING="$(curl -fsS -X POST "$API/raffle/activity/query_credit_award_task_by_token" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"awardOrderId":"missing000001"}')"
[ "$(printf '%s' "$MISSING" | json_field "d['data']['state']")" = "missing" ] || fail "missing task: $MISSING"
pass "credit award task missing state"

CHAT_REQ="chat-$REQ"
DED="$(curl -fsS -X POST "$API/raffle/activity/chat_credit_deduct_by_token?amount=2&requestId=$CHAT_REQ" \
  -H "Authorization: Bearer $TOKEN")"
[ "$(printf '%s' "$DED" | json_field "d['code']")" = "0000" ] || fail "chat deduct: $DED"
BAL_CHAT="$(printf '%s' "$DED" | json_field "d['data']")"
DED_REPLAY="$(curl -fsS -X POST "$API/raffle/activity/chat_credit_deduct_by_token?amount=2&requestId=$CHAT_REQ" \
  -H "Authorization: Bearer $TOKEN")"
[ "$(printf '%s' "$DED_REPLAY" | json_field "d['data']")" = "$BAL_CHAT" ] || fail "chat deduct replay: $DED_REPLAY"
pass "chat deduct"

REF="$(curl -fsS -X POST "$API/internal/raffle/activity/chat_credit_refund_by_token" \
  -H "x-internal-token: ${BM_INTERNAL_TOKEN:-dev-internal-token}" \
  -H 'Content-Type: application/json' \
  -d "{\"userId\":\"xiaofuge\",\"requestId\":\"$CHAT_REQ\"}")"
[ "$(printf '%s' "$REF" | json_field "d['code']")" = "0000" ] || fail "chat refund: $REF"
pass "chat refund"

# --- frontend parity extras (before logout) ---
AWARDS="$(curl -fsS -X POST "$API/raffle/strategy/query_raffle_award_list_by_token" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"activityId":100401}')"
[ "$(printf '%s' "$AWARDS" | json_field "d['code']")" = "0000" ] || fail "award list: $AWARDS"
[ "$(printf '%s' "$AWARDS" | json_field "d['data'][0]['awardId']")" = "101" ] || fail "award list empty: $AWARDS"
UNLOCK="$(printf '%s' "$AWARDS" | json_field "d['data'][0].get('isAwardUnlock')")"
[ "$UNLOCK" = "True" ] || [ "$UNLOCK" = "true" ] || fail "award list unlock: $AWARDS"
pass "strategy award list (lock fields)"

DISPLAY="$(curl -fsS "$API/admin/config/public/display?activityId=100401")"
[ "$(printf '%s' "$DISPLAY" | json_field "d['data']['state']")" = "online" ] || fail "display: $DISPLAY"
pass "admin public display"

ASK="$(curl -fsS -X POST "$API/chatbot/ask" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d "{\"requestId\":\"ask-$REQ\",\"activityId\":100401,\"message\":\"查询积分\"}")"
[ "$(printf '%s' "$ASK" | json_field "d['code']")" = "0000" ] || fail "chatbot ask: $ASK"
SUCCESS="$(printf '%s' "$ASK" | json_field "d['data']['success']")"
[ "$SUCCESS" = "True" ] || [ "$SUCCESS" = "true" ] || fail "chatbot success: $ASK"
pass "chatbot ask"

STAGES="$(curl -fsS "$API/raffle/erp/query_raffle_activity_stage_list" -H "Authorization: Bearer $ADMIN_TOKEN")"
[ "$(printf '%s' "$STAGES" | json_field "d['code']")" = "0000" ] || fail "erp stages: $STAGES"
pass "erp stage list"

ADMIN_LIST="$(curl -fsS "$API/admin/config/list" -H "Authorization: Bearer $ADMIN_TOKEN")"
[ "$(printf '%s' "$ADMIN_LIST" | json_field "d['code']")" = "0000" ] || fail "admin list: $ADMIN_LIST"
pass "admin config list"

curl -fsS -X POST "$API/auth/logout" -H "Authorization: Bearer $TOKEN" >/dev/null
CODE="$(curl -s -o /tmp/rust-verify.json -w '%{http_code}' "$API/auth/verify" -H "Authorization: Bearer $TOKEN" || true)"
BODY_CODE="$(python3 -c "import json; print(json.load(open('/tmp/rust-verify.json')).get('code',''))" 2>/dev/null || true)"
[ "$CODE" = "401" ] || [ "$BODY_CODE" = "0009" ] \
  || fail "expected revoked token rejected (http=$CODE body_code=$BODY_CODE)"
pass "logout revoke"

echo
echo "All smoke checks passed."

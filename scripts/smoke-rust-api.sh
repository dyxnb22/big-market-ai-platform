#!/usr/bin/env bash
# Smoke Rust API (memory stack) — auth, exchange, draw, credit round-trip.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
API="${API:-http://127.0.0.1:8080/api/v1}"

pass() { echo "  PASS  $*"; }
fail() { echo "  FAIL  $*" >&2; exit 1; }

json_field() {
  python3 -c "import json,sys; d=json.load(sys.stdin); print($1)"
}

if ! curl -fsS "${API%/api/v1}/health" >/dev/null 2>&1; then
  "$ROOT/scripts/run-rust-stack.sh"
fi

echo "=== Rust API smoke ==="

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

DRAW="$(curl -fsS -X POST "$API/raffle/activity/draw_by_token" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"activityId":100401}')"
[ "$(printf '%s' "$DRAW" | json_field "d['data']['awardId']")" = "101" ] || fail "draw: $DRAW"
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

CHAT_REQ="chat-$REQ"
DED="$(curl -fsS -X POST "$API/raffle/activity/chat_credit_deduct_by_token?amount=2&requestId=$CHAT_REQ" \
  -H "Authorization: Bearer $TOKEN")"
[ "$(printf '%s' "$DED" | json_field "d['code']")" = "0000" ] || fail "chat deduct: $DED"
pass "chat deduct"

REF="$(curl -fsS -X POST "$API/internal/raffle/activity/chat_credit_refund_by_token" \
  -H "x-internal-token: ${BM_INTERNAL_TOKEN:-dev-internal-token}" \
  -H 'Content-Type: application/json' \
  -d "{\"userId\":\"xiaofuge\",\"requestId\":\"$CHAT_REQ\"}")"
[ "$(printf '%s' "$REF" | json_field "d['code']")" = "0000" ] || fail "chat refund: $REF"
pass "chat refund"

curl -fsS -X POST "$API/auth/logout" -H "Authorization: Bearer $TOKEN" >/dev/null
CODE="$(curl -s -o /tmp/rust-verify.json -w '%{http_code}' "$API/auth/verify" -H "Authorization: Bearer $TOKEN" || true)"
BODY_CODE="$(python3 -c "import json; print(json.load(open('/tmp/rust-verify.json')).get('code',''))" 2>/dev/null || true)"
[ "$CODE" = "401" ] || [ "$BODY_CODE" = "0009" ] \
  || fail "expected revoked token rejected (http=$CODE body_code=$BODY_CODE)"
pass "logout revoke"

echo
echo "All Rust smoke checks passed."

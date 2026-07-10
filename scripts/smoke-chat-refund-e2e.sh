#!/usr/bin/env bash
# Chat credit refund E2E: AI failure immediate refund + pending reconcile via XXL job.
set -euo pipefail

API="${API:-http://127.0.0.1:8080/api/v1}"
XXL_ADMIN="${XXL_ADMIN:-http://127.0.0.1:9090/xxl-job-admin}"
MYSQL_CONTAINER="${MYSQL_CONTAINER:-mysql}"
MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:-123456}"
USER_ID="${USER_ID:-xiaofuge}"

pass() { echo "  PASS  $*"; }
fail() { echo "  FAIL  $*"; exit 1; }

CHAT_REFUND_JOB_ID="$(docker exec "$MYSQL_CONTAINER" mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -N -e \
  "SELECT id FROM xxl_job.xxl_job_info WHERE executor_handler='ChatRefundReconcileJob' LIMIT 1;" 2>/dev/null || true)"
if [ -z "$CHAT_REFUND_JOB_ID" ]; then
  fail "ChatRefundReconcileJob not found in xxl_job_info (run ./scripts/apply-xxl-job-seeds.sh)"
fi

json_field() {
  python3 -c "import json,sys; d=json.load(sys.stdin); print($1)" 2>/dev/null
}

echo "=== Chat Refund E2E ==="
echo

LOGIN="$(curl -fsS "$API/auth/login" -H 'Content-Type: application/json' \
  -d "{\"userId\":\"$USER_ID\",\"password\":\"demo\"}")"
TOKEN="$(printf '%s' "$LOGIN" | json_field "d['data']['token']")"
ADMIN_LOGIN="$(curl -fsS "$API/auth/login" -H 'Content-Type: application/json' \
  -d '{"userId":"admin","password":"admin"}')"
ADMIN_TOKEN="$(printf '%s' "$ADMIN_LOGIN" | json_field "d['data']['token']")"
test -n "$TOKEN" && test -n "$ADMIN_TOKEN"

balance_before="$(curl -fsS "$API/raffle/activity/query_user_credit_account_by_token" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{}' \
  | json_field "d['data']")"

echo "--- Part 1: AI failure with immediate refund (market up) ---"
ORIG_API_KEY="$(curl -fsS "$API/admin/config/get?namespace=chatbot&configKey=apiKey" \
  -H "Authorization: Bearer $ADMIN_TOKEN" | json_field "d.get('data') or {}.get('configValue','')")"
ORIG_PROVIDER="$(curl -fsS "$API/admin/config/get?namespace=chatbot&configKey=provider" \
  -H "Authorization: Bearer $ADMIN_TOKEN" | json_field "d.get('data') or {}.get('configValue','deepseek')")"

restore_config() {
  python3 - "$API" "$ADMIN_TOKEN" "$ORIG_API_KEY" "$ORIG_PROVIDER" <<'PY'
import json, sys, urllib.request
api, token, api_key, provider = sys.argv[1:5]
headers = {"Authorization": f"Bearer {token}", "Content-Type": "application/json"}
for key, value in [("apiKey", api_key), ("provider", provider)]:
    body = json.dumps({"namespace": "chatbot", "configKey": key, "configValue": value, "description": "restore"}).encode()
    req = urllib.request.Request(f"{api}/admin/config/save", data=body, headers=headers, method="POST")
    urllib.request.urlopen(req).read()
PY
}
trap restore_config EXIT

curl -fsS "$API/admin/config/save" -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"namespace":"chatbot","configKey":"provider","configValue":"deepseek","description":"e2e"}' >/dev/null
curl -fsS "$API/admin/config/save" -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"namespace":"chatbot","configKey":"apiKey","configValue":"invalid-e2e-key","description":"e2e"}' >/dev/null
sleep 2

REQ_AI="e2e-ai-fail-$(date +%s)"
ASK_CODE="$(curl -sS "$API/chatbot/ask" -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{\"requestId\":\"$REQ_AI\",\"message\":\"e2e refund test\"}" | json_field "d['code']")"
if [ "$ASK_CODE" != "0001" ]; then
  fail "expected chatbot ask code 0001 after AI failure, got $ASK_CODE"
fi

balance_after_ai="$(curl -fsS "$API/raffle/activity/query_user_credit_account_by_token" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{}' \
  | json_field "d['data']")"
python3 - "$balance_before" "$balance_after_ai" <<'PY'
import sys
from decimal import Decimal
before, after = (Decimal(sys.argv[1]), Decimal(sys.argv[2]))
if before != after:
    raise SystemExit(f"balance not restored after AI fail refund: before={before} after={after}")
PY
pass "AI failure refunded credit (balance $balance_before -> $balance_after_ai)"

echo
echo "--- Part 2: pending session reconcile via ChatRefundReconcileJob ---"
REQ_PENDING="e2e-pending-$(date +%s)"
DEDUCT_BAL="$(curl -fsS -X POST \
  "$API/raffle/activity/chat_credit_deduct_by_token?amount=1&requestId=$REQ_PENDING" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{}' \
  | json_field "d['data']")"

docker exec "$MYSQL_CONTAINER" mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -N -e \
  "UPDATE big_market_01.chat_credit_session SET refund_state='pending'
   WHERE user_id='$USER_ID' AND request_id='$REQ_PENDING';"

STATE_BEFORE="$(docker exec "$MYSQL_CONTAINER" mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -N -e \
  "SELECT refund_state FROM big_market_01.chat_credit_session
   WHERE user_id='$USER_ID' AND request_id='$REQ_PENDING';")"
[ "$STATE_BEFORE" = "pending" ] || fail "pending row not set (got '$STATE_BEFORE')"

COOKIE_JAR="$(mktemp)"
curl -fsS -c "$COOKIE_JAR" -X POST "$XXL_ADMIN/login" \
  -d 'userName=admin&password=123456' >/dev/null
curl -fsS -b "$COOKIE_JAR" -X POST "$XXL_ADMIN/jobinfo/trigger" \
  -d "id=$CHAT_REFUND_JOB_ID&executorParam=&addressList=" >/dev/null
rm -f "$COOKIE_JAR"
sleep 5

STATE_AFTER="$(docker exec "$MYSQL_CONTAINER" mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -N -e \
  "SELECT refund_state FROM big_market_01.chat_credit_session
   WHERE user_id='$USER_ID' AND request_id='$REQ_PENDING';")"
[ "$STATE_AFTER" = "refunded" ] || fail "expected refund_state=refunded, got '$STATE_AFTER'"

balance_final="$(curl -fsS "$API/raffle/activity/query_user_credit_account_by_token" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{}' \
  | json_field "d['data']")"
python3 - "$balance_before" "$balance_final" <<'PY'
import sys
from decimal import Decimal
before, final = (Decimal(sys.argv[1]), Decimal(sys.argv[2]))
if before != final:
    raise SystemExit(f"balance not restored after reconcile: before={before} final={final}")
PY
pass "pending reconcile refunded credit (balance restored to $balance_before)"

echo
echo "=== Chat Refund E2E: ALL PASSED ==="

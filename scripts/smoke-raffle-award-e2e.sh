#!/usr/bin/env bash
# Real default-stage raffle closure:
# credit exchange -> quota -> draw -> award record -> award-credit outbox -> account credit.
set -euo pipefail

API="${API:-http://127.0.0.1:8080/api/v1}"
BASE_URL="${API%/api/v1}"
XXL_ADMIN="${XXL_ADMIN:-http://127.0.0.1:9090/xxl-job-admin}"
MYSQL_CONTAINER="${MYSQL_CONTAINER:-mysql}"
MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:-123456}"
XXL_JOB_ADMIN_USER="${XXL_JOB_ADMIN_USER:-admin}"
XXL_JOB_ADMIN_PASSWORD="${XXL_JOB_ADMIN_PASSWORD:-123456}"
USER_ID="${USER_ID:-${DEMO_USER_ID:-xiaofuge}}"
DEMO_USER_PASSWORD="${DEMO_USER_PASSWORD:-demo}"
DEMO_ADMIN_USER_ID="${DEMO_ADMIN_USER_ID:-admin}"
DEMO_ADMIN_PASSWORD="${DEMO_ADMIN_PASSWORD:-admin}"
CHANNEL="${CHANNEL:-c01}"
SOURCE="${SOURCE:-s01}"

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck source=lib/health-poll.sh
source "$ROOT/scripts/lib/health-poll.sh"

pass() { echo "  PASS  $*"; }
fail() { echo "  FAIL  $*" >&2; exit 1; }

mysql_query() {
  docker exec "$MYSQL_CONTAINER" mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -N -e "$1" 2>/dev/null
}

json_field() {
  python3 -c "import json,sys; d=json.load(sys.stdin); print($1)" 2>/dev/null
}

echo "=== Raffle Award E2E ==="
echo

wait_for_xxl_executor "big-market-message-job" 120 || fail "XXL executor is not registered"

LOGIN="$(curl -fsS "$API/auth/login" -H 'Content-Type: application/json' \
  -d "{\"userId\":\"$USER_ID\",\"password\":\"$DEMO_USER_PASSWORD\"}")"
TOKEN="$(printf '%s' "$LOGIN" | json_field "d['data']['token']")"
ADMIN_LOGIN="$(curl -fsS "$API/auth/login" -H 'Content-Type: application/json' \
  -d "{\"userId\":\"$DEMO_ADMIN_USER_ID\",\"password\":\"$DEMO_ADMIN_PASSWORD\"}")"
ADMIN_TOKEN="$(printf '%s' "$ADMIN_LOGIN" | json_field "d['data']['token']")"
[ -n "$TOKEN" ] && [ -n "$ADMIN_TOKEN" ] || fail "demo login failed"

ACTIVITY_ID="$(resolve_stage_activity_id "$BASE_URL" "$CHANNEL" "$SOURCE")"
[ "$ACTIVITY_ID" = "100401" ] || fail "expected staged activity 100401, got $ACTIVITY_ID"

ARMORY="$(curl -fsS "$API/raffle/activity/armory?activityId=$ACTIVITY_ID" \
  -H "Authorization: Bearer $ADMIN_TOKEN")"
[ "$(printf '%s' "$ARMORY" | json_field "d['code']")" = "0000" ] || fail "activity armory failed: $ARMORY"

DB_SCHEMA=""
for schema in big_market_01 big_market_02; do
  count="$(mysql_query "SELECT COUNT(*) FROM ${schema}.user_credit_account WHERE user_id='${USER_ID}';" || true)"
  if [ "${count:-0}" -gt 0 ]; then
    DB_SCHEMA="$schema"
    break
  fi
done
[ -n "$DB_SCHEMA" ] || fail "cannot locate credit shard for $USER_ID"

if [ "$DB_SCHEMA" = "big_market_01" ]; then
  DISPATCH_JOB_ID=5
else
  DISPATCH_JOB_ID=6
fi

balance_before="$(curl -fsS "$API/raffle/activity/query_user_credit_account_by_token" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{}' \
  | json_field "d['data']")"
task_before="$(mysql_query "SELECT COALESCE(MAX(id),0) FROM ${DB_SCHEMA}.task;")"

REQUEST_ID="freeze-e2e-$(date +%s)-$$"
EXCHANGE="$(curl -fsS -X POST "$API/raffle/activity/credit_pay_exchange_sku_by_token" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d "{\"sku\":9901,\"requestId\":\"$REQUEST_ID\"}")"
[ "$(printf '%s' "$EXCHANGE" | json_field "d['code']")" = "0000" ] \
  || fail "credit exchange failed (balance=$balance_before): $EXCHANGE"
balance_after_exchange="$(curl -fsS "$API/raffle/activity/query_user_credit_account_by_token" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{}' \
  | json_field "d['data']")"
python3 - "$balance_before" "$balance_after_exchange" <<'PY'
import sys
from decimal import Decimal
before, after = map(Decimal, sys.argv[1:])
if before - after != Decimal("5.00"):
    raise SystemExit(f"exchange did not deduct exactly 5 credits: {before} -> {after}")
PY
pass "credit exchange deducted 5 and created draw quota"

DRAW="$(curl -fsS -X POST "$API/raffle/activity/draw_by_token" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d "{\"activityId\":$ACTIVITY_ID}")"
DRAW_CODE="$(printf '%s' "$DRAW" | json_field "d['code']")"
AWARD_ID="$(printf '%s' "$DRAW" | json_field "d.get('data',{}).get('awardId','')")"
[ "$DRAW_CODE" = "0000" ] && [ "$AWARD_ID" = "101" ] \
  || fail "default-stage draw must deterministically return local award 101: $DRAW"
pass "draw returned deterministic local credit award 101"

ORDER_ID=""
for _ in $(seq 1 20); do
  ORDER_ID="$(mysql_query "SELECT JSON_UNQUOTE(JSON_EXTRACT(message,'$.data.orderId'))
    FROM ${DB_SCHEMA}.task
    WHERE id > ${task_before} AND user_id='${USER_ID}' AND topic='send_award'
    ORDER BY id DESC LIMIT 1;" || true)"
  [ -n "$ORDER_ID" ] && break
  sleep 1
done
[ -n "$ORDER_ID" ] || fail "draw created no send_award task"

COOKIE_JAR="$(mktemp)"
trap 'rm -f "$COOKIE_JAR"' EXIT
curl -fsS -c "$COOKIE_JAR" -X POST "$XXL_ADMIN/login" \
  --data-urlencode "userName=$XXL_JOB_ADMIN_USER" \
  --data-urlencode "password=$XXL_JOB_ADMIN_PASSWORD" >/dev/null
TRIGGER="$(curl -fsS -b "$COOKIE_JAR" -X POST "$XXL_ADMIN/jobinfo/trigger" \
  -d "id=$DISPATCH_JOB_ID&executorParam=&addressList=")"
[ "$(printf '%s' "$TRIGGER" | json_field "d.get('code','')")" = "200" ] \
  || fail "award-credit dispatch trigger failed: $TRIGGER"

award_state=""
outbox_state=""
credit_order_amount=""
for _ in $(seq 1 30); do
  award_state="$(mysql_query "SELECT award_state FROM (
      SELECT order_id,award_state FROM ${DB_SCHEMA}.user_award_record_000
      UNION ALL SELECT order_id,award_state FROM ${DB_SCHEMA}.user_award_record_001
      UNION ALL SELECT order_id,award_state FROM ${DB_SCHEMA}.user_award_record_002
      UNION ALL SELECT order_id,award_state FROM ${DB_SCHEMA}.user_award_record_003
    ) r WHERE order_id='${ORDER_ID}' LIMIT 1;" || true)"
  outbox_state="$(mysql_query "SELECT state FROM (
      SELECT award_order_id,state FROM ${DB_SCHEMA}.credit_award_task_000
      UNION ALL SELECT award_order_id,state FROM ${DB_SCHEMA}.credit_award_task_001
      UNION ALL SELECT award_order_id,state FROM ${DB_SCHEMA}.credit_award_task_002
      UNION ALL SELECT award_order_id,state FROM ${DB_SCHEMA}.credit_award_task_003
    ) t WHERE award_order_id='${ORDER_ID}' LIMIT 1;" || true)"
  credit_order_amount="$(mysql_query "SELECT trade_amount FROM (
      SELECT out_business_no,trade_amount FROM ${DB_SCHEMA}.user_credit_order_000
      UNION ALL SELECT out_business_no,trade_amount FROM ${DB_SCHEMA}.user_credit_order_001
      UNION ALL SELECT out_business_no,trade_amount FROM ${DB_SCHEMA}.user_credit_order_002
      UNION ALL SELECT out_business_no,trade_amount FROM ${DB_SCHEMA}.user_credit_order_003
    ) c WHERE out_business_no='${ORDER_ID}' LIMIT 1;" || true)"
  if [ "$award_state" = "completed" ] && [ "$outbox_state" = "dispatched" ] \
      && [ "$credit_order_amount" = "5.00" ]; then
    break
  fi
  sleep 1
done

[ "$award_state" = "completed" ] || fail "award record not completed (order=$ORDER_ID state=$award_state)"
[ "$outbox_state" = "dispatched" ] || fail "award-credit outbox not dispatched (order=$ORDER_ID state=$outbox_state)"
[ "$credit_order_amount" = "5.00" ] || fail "account credit order missing/wrong (order=$ORDER_ID amount=$credit_order_amount)"

balance_final="$(curl -fsS "$API/raffle/activity/query_user_credit_account_by_token" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{}' \
  | json_field "d['data']")"
python3 - "$balance_after_exchange" "$balance_final" <<'PY'
import sys
from decimal import Decimal
after_exchange, final = map(Decimal, sys.argv[1:])
if final - after_exchange < Decimal("5.00"):
    raise SystemExit(f"credited less than 5 after draw: {after_exchange} -> {final}")
PY
pass "award record completed, outbox dispatched, account credited 5 (order=$ORDER_ID)"

echo
echo "=== Raffle Award E2E: ALL PASSED ==="

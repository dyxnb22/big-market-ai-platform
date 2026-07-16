#!/usr/bin/env bash
# Strategy lite smoke: lock-demo activity 100402 + optional chain blacklist.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
API="${API:-http://127.0.0.1:8080/api/v1}"
RS="$ROOT/big-market-rs"

pass() { echo "  PASS  $*"; }
fail() { echo "  FAIL  $*" >&2; exit 1; }

json_field() {
  python3 -c "import json,sys; d=json.load(sys.stdin); print($1)"
}

# Fresh file backend so strategy env does not collide with default smoke state.
export BM_DATA_DIR="${BM_DATA_DIR:-$RS/target/run/strategy-data-$$}"
export BM_STRATEGY_CHAIN=1
export BM_RULE_BLACKLIST="201:xiaofuge"
mkdir -p "$BM_DATA_DIR"
"$ROOT/scripts/run-stack.sh"

echo "=== Strategy smoke ==="

LOGIN="$(curl -fsS "$API/auth/login" -H 'Content-Type: application/json' \
  -d '{"userId":"xiaofuge","password":"demo"}')"
TOKEN="$(printf '%s' "$LOGIN" | json_field "d['data']['token']")"
[ -n "$TOKEN" ] || fail "login: $LOGIN"

STAGE="$(curl -fsS "$API/raffle/activity/query_stage_activity_id?channel=c02&source=s02")"
[ "$(printf '%s' "$STAGE" | json_field "d['data']")" = "100402" ] || fail "stage c02: $STAGE"
pass "stage activity 100402"

ADMIN_LOGIN="$(curl -fsS "$API/auth/login" -H 'Content-Type: application/json' \
  -d '{"userId":"admin","password":"admin"}')"
ADMIN_TOKEN="$(printf '%s' "$ADMIN_LOGIN" | json_field "d['data']['token']")"
ARMORY="$(curl -fsS "$API/raffle/activity/armory?activityId=100402" \
  -H "Authorization: Bearer $ADMIN_TOKEN")"
[ "$(printf '%s' "$ARMORY" | json_field "d['code']")" = "0000" ] || fail "armory: $ARMORY"
pass "armory 100402"

AWARDS="$(curl -fsS -X POST "$API/raffle/strategy/query_raffle_award_list_by_token" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"activityId":100402}')"
[ "$(printf '%s' "$AWARDS" | json_field "d['code']")" = "0000" ] || fail "award list: $AWARDS"
COUNT="$(printf '%s' "$AWARDS" | json_field "len(d['data'])")"
[ "$COUNT" = "4" ] || fail "expected 4 awards: $AWARDS"
LOCKED="$(printf '%s' "$AWARDS" | python3 -c "
import json,sys
d=json.load(sys.stdin)['data']
locked=[a for a in d if a.get('isAwardUnlock') is False]
print(len(locked))
")"
[ "$LOCKED" = "2" ] || fail "expected 2 locked awards at 0 draws: $AWARDS"
WAIT="$(printf '%s' "$AWARDS" | python3 -c "
import json,sys
d=json.load(sys.stdin)['data']
a=next(x for x in d if x['awardId']==203)
print(a.get('waitUnLockCount'))
")"
[ "$WAIT" = "3" ] || fail "award 203 waitUnLockCount: $AWARDS"
pass "lock fields on activity 100402"

DRAW="$(curl -fsS -X POST "$API/raffle/activity/draw_by_token" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"activityId":100402}')"
[ "$(printf '%s' "$DRAW" | json_field "d['code']")" = "0000" ] || fail "draw: $DRAW"
[ "$(printf '%s' "$DRAW" | json_field "d['data']['awardId']")" = "201" ] || fail "blacklist draw: $DRAW"
RULES="$(printf '%s' "$DRAW" | json_field "','.join(d['data']['strategyTrace']['rulesApplied'])")"
echo "$RULES" | grep -q "rule_blacklist" || fail "trace missing blacklist: $DRAW"
pass "strategy chain blacklist + trace"

# Restore default stack without chain for subsequent acceptance steps.
unset BM_STRATEGY_CHAIN BM_RULE_BLACKLIST
export BM_DATA_DIR="$RS/target/run/data"
"$ROOT/scripts/run-stack.sh"

echo
echo "Strategy smoke checks passed."

#!/usr/bin/env bash
# HTTP status + business code contract checks. Fail-closed. Requires running stack.
set -euo pipefail

API="${API:-http://127.0.0.1:8080/api/v1}"
BASE_URL="${API%/api/v1}"
CHANNEL="${CHANNEL:-c01}"
SOURCE="${SOURCE:-s01}"
DEMO_USER_ID="${DEMO_USER_ID:-xiaofuge}"
DEMO_USER_PASSWORD="${DEMO_USER_PASSWORD:-demo}"

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck source=lib/health-poll.sh
source "$ROOT/scripts/lib/health-poll.sh"

echo "=== HTTP Contract Tests ==="
echo

# stage activity = 100401
STAGE_BODY=$(mktemp)
STAGE_HTTP=$(curl -sS -o "$STAGE_BODY" -w '%{http_code}' \
  "$API/raffle/activity/query_stage_activity_id?channel=${CHANNEL}&source=${SOURCE}" || echo "000")
assert_http_and_code "stage activity id" "200" "0000" "$STAGE_BODY" "$STAGE_HTTP"
STAGE_ID=$(python3 -c "import json; print(json.load(open('$STAGE_BODY')).get('data',''))" 2>/dev/null || echo "")
rm -f "$STAGE_BODY"
if [ "$STAGE_ID" != "100401" ]; then
  echo "  FAIL  stage activity data (expected 100401, got $STAGE_ID)" >&2
  exit 1
fi
echo "  PASS  stage activity data=100401"

# admin without token
ADMIN_BODY=$(mktemp)
ADMIN_HTTP=$(curl -sS -o "$ADMIN_BODY" -w '%{http_code}' "$API/admin/config/list" || echo "000")
assert_http_and_code "admin list no auth" "401" "0009" "$ADMIN_BODY" "$ADMIN_HTTP"
rm -f "$ADMIN_BODY"

# normal user token on admin
LOGIN=$(curl -fsS "$API/auth/login" -H 'Content-Type: application/json' \
  -d "{\"userId\":\"$DEMO_USER_ID\",\"password\":\"$DEMO_USER_PASSWORD\"}")
TOKEN=$(printf '%s' "$LOGIN" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['token'])")
USER_ADMIN_BODY=$(mktemp)
USER_ADMIN_HTTP=$(curl -sS -o "$USER_ADMIN_BODY" -w '%{http_code}' \
  "$API/admin/config/list" -H "Authorization: $TOKEN" || echo "000")
assert_http_and_code "admin list normal user" "403" "0008" "$USER_ADMIN_BODY" "$USER_ADMIN_HTTP"
rm -f "$USER_ADMIN_BODY"

# draw without token
DRAW_BODY=$(mktemp)
DRAW_HTTP=$(curl -sS -o "$DRAW_BODY" -w '%{http_code}' -X POST \
  "$API/raffle/activity/draw_by_token" \
  -H "Content-Type: application/json" \
  -d "{\"activityId\":${STAGE_ID}}" || echo "000")
assert_http_and_code "draw no token" "401" "0009" "$DRAW_BODY" "$DRAW_HTTP"
rm -f "$DRAW_BODY"

# login empty password
LOGIN_BODY=$(mktemp)
LOGIN_HTTP=$(curl -sS -o "$LOGIN_BODY" -w '%{http_code}' -X POST "$API/auth/login" \
  -H 'Content-Type: application/json' \
  -d "{\"userId\":\"$DEMO_USER_ID\",\"password\":\"\"}" || echo "000")
assert_http_and_code "login empty password" "400" "0002" "$LOGIN_BODY" "$LOGIN_HTTP"
rm -f "$LOGIN_BODY"

echo
echo "=== HTTP Contract Tests ALL PASSED ==="

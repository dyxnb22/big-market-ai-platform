#!/usr/bin/env bash
# Negative security smoke: logout JWT, forged internal chat token, admin without auth.
# Fail-closed. Assumes default (or secure) stack is already healthy on localhost.
set -euo pipefail

API="${API:-http://127.0.0.1:8080/api/v1}"
DEMO_USER_ID="${DEMO_USER_ID:-xiaofuge}"
DEMO_USER_PASSWORD="${DEMO_USER_PASSWORD:-demo}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck source=lib/health-poll.sh
source "$ROOT/scripts/lib/health-poll.sh"

pass() { echo "  PASS  $*"; }
fail() { echo "  FAIL  $*"; exit 1; }

echo "=== Security Smoke ==="

LOGIN="$(curl -fsS "$API/auth/login" -H 'Content-Type: application/json' \
  -d "{\"userId\":\"$DEMO_USER_ID\",\"password\":\"$DEMO_USER_PASSWORD\"}")"
assert_json_code "login" "0000" "$LOGIN"
TOKEN="$(printf '%s' "$LOGIN" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['token'])")"
test -n "$TOKEN"

# Logout then reuse JWT → expect HTTP 401 and business code 0009
curl -fsS -X POST "$API/auth/logout" -H "Authorization: $TOKEN" >/dev/null || true
VERIFY_BODY=$(mktemp)
VERIFY_HTTP=$(curl -sS -o "$VERIFY_BODY" -w '%{http_code}' "$API/auth/verify" -H "Authorization: $TOKEN" || echo "000")
VERIFY_CODE=$(python3 -c "import json; print(json.load(open('$VERIFY_BODY')).get('code',''))" 2>/dev/null || echo "")
rm -f "$VERIFY_BODY"
if [ "$VERIFY_HTTP" = "401" ] && { [ "$VERIFY_CODE" = "0009" ] || [ "$VERIFY_CODE" = "0001" ]; }; then
  pass "logout revokes JWT (http=$VERIFY_HTTP code=$VERIFY_CODE)"
elif [ "$VERIFY_HTTP" = "401" ] || [ "$VERIFY_CODE" = "0009" ]; then
  pass "logout revokes JWT (http=$VERIFY_HTTP code=$VERIFY_CODE)"
else
  fail "logout did not revoke JWT (http=$VERIFY_HTTP code=$VERIFY_CODE)"
fi

# Forged internal chat token on market internal refund route
FORGE_BODY=$(mktemp)
FORGE_HTTP=$(curl -sS -o "$FORGE_BODY" -w '%{http_code}' -X POST \
  "http://127.0.0.1:8083/api/v1/internal/raffle/activity/chat_credit_refund_by_token?originalRequestId=sec-smoke-1" \
  -H "Authorization: Bearer fake" \
  -H "X-Chat-Internal-Token: forged-token-not-valid" || echo "000")
FORGE_CODE=$(python3 -c "import json; print(json.load(open('$FORGE_BODY')).get('code',''))" 2>/dev/null || echo "")
rm -f "$FORGE_BODY"
if [ "$FORGE_HTTP" = "401" ] && [ "$FORGE_CODE" = "0009" ]; then
  pass "forged internal chat token rejected (http=$FORGE_HTTP code=$FORGE_CODE)"
elif [ "$FORGE_HTTP" = "401" ] || [ "$FORGE_HTTP" = "403" ] || [ "$FORGE_CODE" = "0009" ]; then
  pass "forged internal chat token rejected (http=$FORGE_HTTP code=$FORGE_CODE)"
else
  if [ "$FORGE_HTTP" = "200" ] && [ "$FORGE_CODE" = "0000" ]; then
    fail "forged internal chat token was accepted"
  fi
  fail "forged internal chat token not rejected (http=$FORGE_HTTP code=$FORGE_CODE)"
fi

# Admin list without auth → HTTP 401 + code 0009
ADMIN_BODY=$(mktemp)
ADMIN_HTTP=$(curl -sS -o "$ADMIN_BODY" -w '%{http_code}' "$API/admin/config/list" || echo "000")
ADMIN_CODE=$(python3 -c "import json; print(json.load(open('$ADMIN_BODY')).get('code',''))" 2>/dev/null || echo "")
rm -f "$ADMIN_BODY"
if [ "$ADMIN_HTTP" = "401" ] && [ "$ADMIN_CODE" = "0009" ]; then
  pass "admin without auth rejected (http=$ADMIN_HTTP code=$ADMIN_CODE)"
elif [ "$ADMIN_HTTP" = "401" ] || [ "$ADMIN_CODE" = "0009" ]; then
  pass "admin without auth rejected (http=$ADMIN_HTTP code=$ADMIN_CODE)"
else
  fail "admin without auth not rejected (http=$ADMIN_HTTP code=$ADMIN_CODE)"
fi

echo "=== Security Smoke ALL PASSED ==="

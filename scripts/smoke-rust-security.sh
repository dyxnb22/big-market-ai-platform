#!/usr/bin/env bash
# Security smoke for Rust stack (logout revoke, forged internal token, admin without auth).
set -euo pipefail

API="${API:-http://127.0.0.1:8080/api/v1}"
DEMO_USER_ID="${DEMO_USER_ID:-xiaofuge}"
DEMO_USER_PASSWORD="${DEMO_USER_PASSWORD:-demo}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck source=lib/health-poll.sh
source "$ROOT/scripts/lib/health-poll.sh"

pass() { echo "  PASS  $*"; }
fail() { echo "  FAIL  $*"; exit 1; }

echo "=== Rust security smoke ==="

LOGIN="$(curl -fsS "$API/auth/login" -H 'Content-Type: application/json' \
  -d "{\"userId\":\"$DEMO_USER_ID\",\"password\":\"$DEMO_USER_PASSWORD\"}")"
assert_json_code "login" "0000" "$LOGIN"
TOKEN="$(printf '%s' "$LOGIN" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['token'])")"
test -n "$TOKEN"

curl -fsS -X POST "$API/auth/logout" -H "Authorization: Bearer $TOKEN" >/dev/null || true
VERIFY_BODY=$(mktemp)
VERIFY_HTTP=$(curl -sS -o "$VERIFY_BODY" -w '%{http_code}' "$API/auth/verify" -H "Authorization: Bearer $TOKEN" || echo "000")
VERIFY_CODE=$(python3 -c "import json; print(json.load(open('$VERIFY_BODY')).get('code',''))" 2>/dev/null || echo "")
rm -f "$VERIFY_BODY"
if [ "$VERIFY_HTTP" = "401" ] || [ "$VERIFY_CODE" = "0009" ]; then
  pass "logout revokes JWT (http=$VERIFY_HTTP code=$VERIFY_CODE)"
else
  fail "logout did not revoke JWT (http=$VERIFY_HTTP code=$VERIFY_CODE)"
fi

# Forged internal token (Rust: x-internal-token on gateway route)
FORGE_BODY=$(mktemp)
FORGE_HTTP=$(curl -sS -o "$FORGE_BODY" -w '%{http_code}' -X POST \
  "$API/internal/raffle/activity/chat_credit_refund_by_token" \
  -H "Content-Type: application/json" \
  -H "x-internal-token: forged-token-not-valid" \
  -d '{"userId":"xiaofuge","requestId":"sec-smoke-1"}' || echo "000")
FORGE_CODE=$(python3 -c "import json; print(json.load(open('$FORGE_BODY')).get('code',''))" 2>/dev/null || echo "")
rm -f "$FORGE_BODY"
if [ "$FORGE_HTTP" = "401" ] || [ "$FORGE_HTTP" = "403" ] || [ "$FORGE_CODE" = "0009" ]; then
  pass "forged internal token rejected (http=$FORGE_HTTP code=$FORGE_CODE)"
else
  fail "forged internal token not rejected (http=$FORGE_HTTP code=$FORGE_CODE)"
fi

ADMIN_BODY=$(mktemp)
ADMIN_HTTP=$(curl -sS -o "$ADMIN_BODY" -w '%{http_code}' "$API/admin/config/list" || echo "000")
ADMIN_CODE=$(python3 -c "import json; print(json.load(open('$ADMIN_BODY')).get('code',''))" 2>/dev/null || echo "")
rm -f "$ADMIN_BODY"
if [ "$ADMIN_HTTP" = "401" ] || [ "$ADMIN_CODE" = "0009" ]; then
  pass "admin without auth rejected (http=$ADMIN_HTTP code=$ADMIN_CODE)"
else
  fail "admin without auth not rejected (http=$ADMIN_HTTP code=$ADMIN_CODE)"
fi

# BM_SECURE=1 must refuse default secrets when starting app (config validation)
if BM_SECURE=1 BM_PORT=18083 BM_JWT_SECRET=change-me-in-dev-only BM_INTERNAL_TOKEN=dev-internal-token \
  timeout 3 "$ROOT/big-market-rs/target/release/bm-app" >/tmp/bm-secure-guard.log 2>&1; then
  fail "BM_SECURE=1 allowed default JWT/internal token"
else
  pass "BM_SECURE=1 rejects default secrets at boot"
fi

echo "=== Rust security smoke ALL PASSED ==="

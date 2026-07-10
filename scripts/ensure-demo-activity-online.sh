#!/usr/bin/env bash
# Ensure the staged demo activity display state is online (Playwright / local demo).
# Resolves stage activity via channel/source (default c01/s01 → 100401). Fail-closed.
set -euo pipefail

GW="${1:-http://127.0.0.1:8080}"
CHANNEL="${CHANNEL:-c01}"
SOURCE="${SOURCE:-s01}"

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck source=lib/health-poll.sh
source "$ROOT/scripts/lib/health-poll.sh"

ADMIN_LOGIN=$(curl -sf -X POST "$GW/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"userId":"admin","password":"admin"}')
ADMIN_TOKEN=$(echo "$ADMIN_LOGIN" | python3 -c "import sys,json; print(json.load(sys.stdin).get('data',{}).get('token',''))" 2>/dev/null || true)

if [ -z "$ADMIN_TOKEN" ]; then
  echo "FAIL: admin login failed; cannot ensure activity online" >&2
  exit 1
fi

STAGE_ID=$(resolve_stage_activity_id "$GW" "$CHANNEL" "$SOURCE")
echo "Resolved stage activityId=${STAGE_ID} (channel=${CHANNEL} source=${SOURCE})"

curl -sf -X POST "$GW/api/v1/admin/config/save" \
  -H "Authorization: $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"namespace\":\"activity.${STAGE_ID}\",\"configKey\":\"state\",\"configValue\":\"online\",\"description\":\"demo online\"}" >/dev/null

# Undo chat-refund E2E leftovers so smoke chatbot/ask succeeds (local provider, no API key).
for payload in \
  '{"namespace":"chatbot","configKey":"provider","configValue":"local","description":"demo reset"}' \
  '{"namespace":"chatbot","configKey":"apiKey","configValue":"","description":"demo reset"}'; do
  curl -sf -X POST "$GW/api/v1/admin/config/save" \
    -H "Authorization: $ADMIN_TOKEN" \
    -H "Content-Type: application/json" \
    -d "$payload" >/dev/null
done

curl -sf -H "X-Admin-Token: ${ADMIN_DEV_TOKEN:-admin-dev-token}" \
  "$GW/api/v1/raffle/activity/armory?activityId=${STAGE_ID}" >/dev/null

state=$(curl -sf "$GW/api/v1/admin/config/public/display?activityId=${STAGE_ID}" \
  | python3 -c "import sys,json; print(json.load(sys.stdin).get('data',{}).get('state',''))" 2>/dev/null || true)
echo "activity.${STAGE_ID} display state=${state:-unknown}"

if [ "$state" != "online" ] && [ "$state" != "active" ]; then
  echo "FAIL: activity ${STAGE_ID} is not online/active (state=${state:-empty})" >&2
  exit 1
fi

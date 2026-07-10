#!/usr/bin/env bash
# Ensure the staged demo activity display state is online (Playwright / local demo).
# Resolves stage activity via channel/source (default c01/s01 → 100401), then
# also ensures fallback 100301 is online.
set -euo pipefail

GW="${1:-http://127.0.0.1:8080}"
CHANNEL="${CHANNEL:-c01}"
SOURCE="${SOURCE:-s01}"
FALLBACK_ACTIVITY_ID="${FALLBACK_ACTIVITY_ID:-100301}"

ADMIN_LOGIN=$(curl -sf -X POST "$GW/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"userId":"admin","password":"admin"}' || true)
ADMIN_TOKEN=$(echo "$ADMIN_LOGIN" | python3 -c "import sys,json; print(json.load(sys.stdin).get('data',{}).get('token',''))" 2>/dev/null || true)

if [ -z "$ADMIN_TOKEN" ]; then
  echo "WARN: admin login failed; skip activity online ensure" >&2
  exit 0
fi

STAGE_ID=$(curl -sf "$GW/api/v1/raffle/activity/query_stage_activity_id?channel=${CHANNEL}&source=${SOURCE}" \
  | python3 -c "import sys,json; d=json.load(sys.stdin).get('data'); print(d if d else '')" 2>/dev/null || true)

ensure_online() {
  local activity_id="$1"
  [ -n "$activity_id" ] || return 0
  curl -sf -X POST "$GW/api/v1/admin/config/save" \
    -H "Authorization: $ADMIN_TOKEN" \
    -H "Content-Type: application/json" \
    -d "{\"namespace\":\"activity.${activity_id}\",\"configKey\":\"state\",\"configValue\":\"online\",\"description\":\"demo online\"}" >/dev/null
  # Best-effort armory so award stock trees exist for draws
  curl -sf "$GW/api/v1/raffle/activity/armory?activityId=${activity_id}" >/dev/null || true
  local state
  state=$(curl -sf "$GW/api/v1/admin/config/public/display?activityId=${activity_id}" \
    | python3 -c "import sys,json; print(json.load(sys.stdin).get('data',{}).get('state',''))" 2>/dev/null || true)
  echo "activity.${activity_id} display state=${state:-unknown}"
  [ "$state" = "online" ] || [ "$state" = "active" ]
}

ok=0
if [ -n "$STAGE_ID" ]; then
  ensure_online "$STAGE_ID" && ok=1
fi
ensure_online "$FALLBACK_ACTIVITY_ID" && ok=1
[ "$ok" -eq 1 ]

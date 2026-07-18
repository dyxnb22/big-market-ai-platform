#!/usr/bin/env bash
# Verifies the live Nacos -> admin -> market/chatbot configuration path.
# It always restores the normal final values (degrade=close, chatbot.enabled=true).
set -euo pipefail

API="${API:-http://127.0.0.1:8080/api/v1}"
MYSQL_CONTAINER="${MYSQL_CONTAINER:-mysql}"
MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:-123456}"
MARKET_CONTAINER="${MARKET_CONTAINER:-big-market-market-service}"
CHATBOT_CONTAINER="${CHATBOT_CONTAINER:-big-market-chatbot-service}"
DEMO_USER_ID="${DEMO_USER_ID:-xiaofuge}"
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

json_field() {
  python3 -c "import json,sys; d=json.load(sys.stdin); print($1)" 2>/dev/null
}

save_config() {
  local namespace="$1" key="$2" value="$3" description="$4"
  local response http_code
  response="$(curl -sS -w "\n%{http_code}" -X POST "$API/admin/config/save" \
    -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' \
    -d "{\"namespace\":\"$namespace\",\"configKey\":\"$key\",\"configValue\":\"$value\",\"description\":\"$description\"}")"
  http_code="$(printf '%s' "$response" | tail -n1)"
  response="$(printf '%s' "$response" | sed '$d')"
  [ "$http_code" = "200" ] || fail "admin save $namespace.$key http=$http_code body=$response"
  [ "$(printf '%s' "$response" | json_field "d.get('code','')")" = "0000" ] \
    || fail "admin save $namespace.$key failed: $response"
}

restore_normal_values() {
  if [ -n "${ADMIN_TOKEN:-}" ]; then
    curl -sS -X POST "$API/admin/config/save" \
      -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' \
      -d '{"namespace":"system","configKey":"degradeSwitch","configValue":"close","description":"runtime safety restore"}' \
      >/dev/null || true
    curl -sS -X POST "$API/admin/config/save" \
      -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' \
      -d '{"namespace":"chatbot","configKey":"enabled","configValue":"true","description":"runtime safety restore"}' \
      >/dev/null || true
  fi
}

wait_for_log() {
  local container="$1" since="$2" expected="$3"
  for _ in $(seq 1 30); do
    if docker logs "$container" --since "$since" 2>&1 | grep -Fq "$expected"; then
      return 0
    fi
    if docker logs "$container" --tail 120 2>&1 | grep -Fq "$expected"; then
      return 0
    fi
    sleep 1
  done
  return 1
}

mysql_config_content() {
  local data_id="$1"
  # Prefer empty tenant (learning SoT / Nacos 3.x write target); fall back to public.
  docker exec "$MYSQL_CONTAINER" mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -N -r -e \
    "SELECT content FROM nacos_config.config_info
       WHERE data_id='$data_id' AND group_id='DEFAULT_GROUP'
       ORDER BY CASE WHEN IFNULL(tenant_id,'')='' THEN 0 WHEN tenant_id='public' THEN 1 ELSE 2 END
       LIMIT 1;" 2>/dev/null
}

echo "=== Nacos Runtime Configuration Smoke ==="
echo

USER_LOGIN="$(curl -fsS "$API/auth/login" -H 'Content-Type: application/json' \
  -d "{\"userId\":\"$DEMO_USER_ID\",\"password\":\"$DEMO_USER_PASSWORD\"}")"
USER_TOKEN="$(printf '%s' "$USER_LOGIN" | json_field "d.get('data',{}).get('token','')")"
ADMIN_LOGIN="$(curl -fsS "$API/auth/login" -H 'Content-Type: application/json' \
  -d "{\"userId\":\"$DEMO_ADMIN_USER_ID\",\"password\":\"$DEMO_ADMIN_PASSWORD\"}")"
ADMIN_TOKEN="$(printf '%s' "$ADMIN_LOGIN" | json_field "d.get('data',{}).get('token','')")"
[ -n "$USER_TOKEN" ] && [ -n "$ADMIN_TOKEN" ] || fail "demo or admin login failed"
trap restore_normal_values EXIT

ACTIVITY_ID="$(resolve_stage_activity_id "${API%/api/v1}" "$CHANNEL" "$SOURCE")"
[ -n "$ACTIVITY_ID" ] || fail "could not resolve staged activity"

market_since="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"
save_config system degradeSwitch open "runtime safety smoke"
wait_for_log "$MARKET_CONTAINER" "$market_since" \
  "(pubsub) degradeSwitch=open" \
  || fail "market listener did not report degradeSwitch=open"

DRAW_OPEN="$(curl -sS -X POST "$API/raffle/activity/draw_by_token" \
  -H "Authorization: Bearer $USER_TOKEN" -H 'Content-Type: application/json' \
  -d "{\"activityId\":$ACTIVITY_ID}")"
[ "$(printf '%s' "$DRAW_OPEN" | json_field "d.get('code','')")" = "0004" ] \
  || fail "degrade=open did not reject draw: $DRAW_OPEN"
pass "market listener received open and draw was degraded"

market_since="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"
save_config system degradeSwitch close "runtime safety restore"
wait_for_log "$MARKET_CONTAINER" "$market_since" \
  "(pubsub) degradeSwitch=close" \
  || fail "market listener did not report degradeSwitch=close"

DRAW_CLOSE="$(curl -sS -X POST "$API/raffle/activity/draw_by_token" \
  -H "Authorization: Bearer $USER_TOKEN" -H 'Content-Type: application/json' \
  -d "{\"activityId\":$ACTIVITY_ID}")"
DRAW_CLOSE_CODE="$(printf '%s' "$DRAW_CLOSE" | json_field "d.get('code','')")"
[ -n "$DRAW_CLOSE_CODE" ] && [ "$DRAW_CLOSE_CODE" != "0004" ] \
  || fail "degrade=close did not restore draw path: $DRAW_CLOSE"
pass "market listener received close and draw left degrade path"

chatbot_since="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"
save_config chatbot enabled false "runtime safety smoke"
wait_for_log "$CHATBOT_CONTAINER" "$chatbot_since" "Platform config update received from Nacos" \
  || fail "chatbot listener did not receive chatbot.enabled=false"

CHAT_DISABLED="$(curl -fsS -X POST "$API/chatbot/ask" \
  -H "Authorization: Bearer $USER_TOKEN" -H 'Content-Type: application/json' \
  -d "{\"requestId\":\"nacos-disabled-$(date +%s)-$$\",\"message\":\"runtime config smoke\"}")"
[ "$(printf '%s' "$CHAT_DISABLED" | json_field "d.get('code','')")" = "0000" ] \
  && [ "$(printf '%s' "$CHAT_DISABLED" | json_field "d.get('data',{}).get('toolName','')")" = "disabled" ] \
  || fail "chatbot.enabled=false did not produce disabled response: $CHAT_DISABLED"
pass "chatbot listener received disabled configuration"

chatbot_since="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"
save_config chatbot enabled true "runtime safety restore"
wait_for_log "$CHATBOT_CONTAINER" "$chatbot_since" "Platform config update received from Nacos" \
  || fail "chatbot listener did not receive chatbot.enabled=true"

CHAT_ENABLED="$(curl -sS -X POST "$API/chatbot/ask" \
  -H "Authorization: Bearer $USER_TOKEN" -H 'Content-Type: application/json' \
  -d "{\"requestId\":\"nacos-enabled-$(date +%s)-$$\",\"message\":\"runtime config smoke\"}")"
[ "$(printf '%s' "$CHAT_ENABLED" | json_field "d.get('code','')")" = "0000" ] \
  && [ "$(printf '%s' "$CHAT_ENABLED" | json_field "d.get('data',{}).get('toolName','')")" != "disabled" ] \
  || fail "chatbot.enabled=true did not restore normal chat path: $CHAT_ENABLED"
pass "chatbot listener received enabled configuration"

runtime_content="$(mysql_config_content big-market-runtime-switches)"
platform_content="$(mysql_config_content big-market-platform-config)"
printf '%s' "$runtime_content" | grep -Fqx 'system.degradeSwitch.value=close' \
  || fail "Nacos runtime DataId did not finish at degradeSwitch=close"
printf '%s' "$platform_content" | grep -Fqx 'chatbot.enabled.value=true' \
  || fail "Nacos platform DataId did not finish at chatbot.enabled=true"
pass "Nacos and both runtime listeners finished in normal state"

trap - EXIT
echo
echo "=== Nacos Runtime Configuration Smoke: ALL PASSED ==="

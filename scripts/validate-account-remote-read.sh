#!/usr/bin/env bash
# Validate Phase 2.2-B remote-read routing for account-service.
#
# Safe behavior:
#   - Requires the existing Docker stack to be running.
#   - Recreates only big-market-market-service to flip ACCOUNT_SERVICE_REMOTE_READ_ENABLED.
#   - Does not delete containers, volumes, queues, or database data.
#   - Restores ACCOUNT_SERVICE_REMOTE_READ_ENABLED=false before exit.

set -euo pipefail

HOST="${1:-localhost}"
MARKET="http://$HOST:8083"
USER_ID="${ACCOUNT_REMOTE_READ_USER_ID:-xiaofuge}"
ACTIVITY_ID="${ACCOUNT_REMOTE_READ_ACTIVITY_ID:-100301}"
SERVICE_MARKET="big-market-market-service"
SERVICE_ACCOUNT="big-market-account-service"

PASS=0
FAIL=0
MANUAL=0

restore_stack() {
  echo ""
  echo "--- Restoring default remote-read=false ---"
  if docker compose ps "$SERVICE_ACCOUNT" --status running >/dev/null 2>&1; then
    :
  else
    docker compose start "$SERVICE_ACCOUNT" >/dev/null 2>&1 || true
    wait_health "$SERVICE_ACCOUNT" "http://$HOST:8086/actuator/health" 90 || true
  fi
  ACCOUNT_SERVICE_REMOTE_READ_ENABLED=false docker compose up -d --no-deps --force-recreate "$SERVICE_MARKET" >/dev/null 2>&1 || true
  wait_health "$SERVICE_MARKET" "$MARKET/actuator/health" 120 || true
}

trap restore_stack EXIT

json_code() {
  python3 -c 'import sys,json; print(json.load(sys.stdin).get("code",""))' 2>/dev/null || true
}

check_code() {
  local label="$1" expected="$2" body="$3"
  local code
  code="$(printf '%s' "$body" | json_code)"
  if [ "$code" = "$expected" ]; then
    echo "  PASS  $label code=$expected"
    PASS=$((PASS+1))
  else
    echo "  FAIL  $label expected code=$expected got=${code:-UNPARSEABLE} body=${body:0:180}"
    FAIL=$((FAIL+1))
  fi
}

manual_step() {
  echo "  MANUAL  $1"
  MANUAL=$((MANUAL+1))
}

wait_health() {
  local label="$1" url="$2" timeout="${3:-90}"
  local start now status
  start="$(date +%s)"
  while true; do
    status="$(curl -sf "$url" | python3 -c 'import sys,json; print(json.load(sys.stdin).get("status",""))' 2>/dev/null || true)"
    if [ "$status" = "UP" ]; then
      echo "  PASS  $label health UP"
      return 0
    fi
    now="$(date +%s)"
    if [ $((now - start)) -ge "$timeout" ]; then
      echo "  FAIL  $label health not UP after ${timeout}s"
      return 1
    fi
    sleep 3
  done
}

require_running() {
  local service="$1"
  local state
  state="$(docker compose ps "$service" --format json 2>/dev/null | python3 -c 'import sys,json
raw=sys.stdin.read().strip()
if not raw:
    print("")
else:
    data=json.loads(raw.splitlines()[0])
    print(data.get("State",""))' 2>/dev/null || true)"
  if [ "$state" != "running" ]; then
    echo "ERROR: $service must already be running. Start the Docker stack first; this script will not create the whole stack."
    exit 1
  fi
}

call_form() {
  local path="$1" data="$2"
  curl -sf -X POST "$MARKET/api/v1/$path" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    -d "$data" 2>/dev/null || echo '{"code":"CURL_FAIL"}'
}

call_json() {
  local path="$1" data="$2"
  curl -sf -X POST "$MARKET/api/v1/$path" \
    -H "Content-Type: application/json" \
    --data "$data" 2>/dev/null || echo '{"code":"CURL_FAIL"}'
}

echo "=== Phase 2.2-B Remote Read Validation ==="
echo "userId=$USER_ID activityId=$ACTIVITY_ID host=$HOST"
echo ""

if ! command -v docker >/dev/null 2>&1; then
  echo "ERROR: docker is required."
  exit 1
fi

require_running "$SERVICE_MARKET"
require_running "$SERVICE_ACCOUNT"

echo "--- Baseline health ---"
wait_health "$SERVICE_MARKET" "$MARKET/actuator/health" 30
wait_health "$SERVICE_ACCOUNT" "http://$HOST:8086/actuator/health" 30

echo ""
echo "--- Enable remote reads on market-service only ---"
ACCOUNT_SERVICE_REMOTE_READ_ENABLED=true docker compose up -d --no-deps --force-recreate "$SERVICE_MARKET" >/dev/null
wait_health "$SERVICE_MARKET" "$MARKET/actuator/health" 120
sleep 5

echo ""
echo "--- Read endpoint checks with remote-read=true ---"
CREDIT_BODY="$(call_form "raffle/activity/query_user_credit_account" "userId=$USER_ID")"
check_code "query_user_credit_account" "0000" "$CREDIT_BODY"

ACTIVITY_BODY="$(call_json "raffle/activity/query_user_activity_account" "{\"userId\":\"$USER_ID\",\"activityId\":$ACTIVITY_ID}")"
check_code "query_user_activity_account" "0000" "$ACTIVITY_BODY"

AWARD_BODY="$(call_json "raffle/strategy/query_raffle_award_list" "{\"userId\":\"$USER_ID\",\"activityId\":$ACTIVITY_ID}")"
check_code "query_raffle_award_list" "0000" "$AWARD_BODY"

WEIGHT_BODY="$(call_json "raffle/strategy/query_raffle_strategy_rule_weight" "{\"userId\":\"$USER_ID\",\"activityId\":$ACTIVITY_ID}")"
check_code "query_raffle_strategy_rule_weight" "0000" "$WEIGHT_BODY"

echo ""
echo "--- Adapter log proof ---"
LOG_LINES="$(docker compose logs --since 10m "$SERVICE_MARKET" 2>/dev/null | grep -F "[AccountRemoteReadAdapter]" | tail -20 || true)"
if printf '%s' "$LOG_LINES" | grep -q "remote success"; then
  echo "$LOG_LINES"
  echo "  PASS  AccountRemoteReadAdapter remote success logs found"
  PASS=$((PASS+1))
else
  manual_step "No AccountRemoteReadAdapter remote success logs found. Check provider registration and test data manually with docker compose logs $SERVICE_MARKET."
fi

echo ""
echo "--- Fallback check: stop account-service temporarily ---"
docker compose stop "$SERVICE_ACCOUNT" >/dev/null
sleep 8
FALLBACK_BODY="$(call_form "raffle/activity/query_user_credit_account" "userId=$USER_ID")"
check_code "query_user_credit_account fallback while account-service stopped" "0000" "$FALLBACK_BODY"
FALLBACK_LOGS="$(docker compose logs --since 5m "$SERVICE_MARKET" 2>/dev/null | grep -F "[AccountRemoteReadAdapter]" | grep -E "falling back|non-success" | tail -20 || true)"
if [ -n "$FALLBACK_LOGS" ]; then
  echo "$FALLBACK_LOGS"
  echo "  PASS  AccountRemoteReadAdapter fallback logs found"
  PASS=$((PASS+1))
else
  manual_step "Fallback endpoint returned 0000, but no fallback log was captured. Review market-service logs manually."
fi

echo ""
echo "--- Restart account-service ---"
docker compose start "$SERVICE_ACCOUNT" >/dev/null
wait_health "$SERVICE_ACCOUNT" "http://$HOST:8086/actuator/health" 120

echo ""
echo "=========================================="
echo "Results: $PASS passed, $FAIL failed, $MANUAL manual/partial"
echo "=========================================="

if [ "$FAIL" -ne 0 ]; then
  exit 1
fi

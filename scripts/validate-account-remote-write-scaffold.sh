#!/usr/bin/env bash
# Validate Phase 2.2-B2/B3 write-path adapter scaffold.
#
# Runs two categories of checks:
#   1. Static source checks (no Docker required) — verify adapter wiring in source.
#   2. Docker runtime checks — require the existing Docker stack to be running.
#
# Safe behavior (Docker section):
#   - Recreates only big-market-message-job-service to flip write flags.
#   - Does NOT publish real MQ messages; all write-path validation is structural
#     (health, bean presence in logs) not transactional.
#   - Does not delete containers, volumes, queues, or database data.
#   - Restores both write flags to false before exit.

set -euo pipefail

HOST="${1:-localhost}"
SERVICE_JOB="big-market-message-job-service"
SERVICE_ACCOUNT="big-market-account-service"
JOB_HEALTH="http://$HOST:8085/actuator/health"
ACCOUNT_HEALTH="http://$HOST:8086/actuator/health"

PASS=0
FAIL=0
MANUAL=0

restore_stack() {
  echo ""
  echo "--- Restoring default write flags=false ---"
  ACCOUNT_SERVICE_REMOTE_CREDIT_WRITE_ENABLED=false \
  ACCOUNT_SERVICE_REMOTE_QUOTA_WRITE_ENABLED=false \
  docker compose up -d --no-deps --force-recreate "$SERVICE_JOB" >/dev/null 2>&1 || true
  wait_health "$SERVICE_JOB" "$JOB_HEALTH" 120 || true
  echo "  write flags restored to false"
}

trap restore_stack EXIT

wait_health() {
  local label="$1" url="$2" timeout="${3:-90}"
  local start now status
  start="$(date +%s)"
  while true; do
    status="$(curl -sf "$url" | python3 -c 'import sys,json; print(json.load(sys.stdin).get("status",""))' 2>/dev/null || true)"
    if [ "$status" = "UP" ]; then
      echo "  PASS  $label health UP"
      PASS=$((PASS+1))
      return 0
    fi
    now="$(date +%s)"
    if [ $((now - start)) -ge "$timeout" ]; then
      echo "  FAIL  $label health not UP after ${timeout}s"
      FAIL=$((FAIL+1))
      return 1
    fi
    sleep 3
  done
}

require_running() {
  local service="$1"
  local state
  state="$(docker compose ps "$service" --format json 2>/dev/null | python3 -c '
import sys,json
raw=sys.stdin.read().strip()
if not raw:
    print("")
else:
    data=json.loads(raw.splitlines()[0])
    print(data.get("State",""))' 2>/dev/null || true)"
  if [ "$state" != "running" ]; then
    echo "ERROR: $service must already be running. Start the Docker stack first."
    exit 1
  fi
}

check_flag_env() {
  local service="$1" flag_name="$2" expected="$3"
  local actual
  actual="$(docker compose exec -T "$service" printenv "$flag_name" 2>/dev/null || true)"
  if [ "$actual" = "$expected" ]; then
    echo "  PASS  $service $flag_name=$actual"
    PASS=$((PASS+1))
  else
    echo "  FAIL  $service $flag_name expected=$expected got=${actual:-UNSET}"
    FAIL=$((FAIL+1))
  fi
}

echo "=== Phase 2.2-B2/B3 Write-Scaffold Validation ==="
echo "host=$HOST"
echo ""

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CONTROLLER="$REPO_ROOT/big-market-trigger/src/main/java/com/dyx/market/trigger/http/RaffleActivityController.java"

# ─── Static source checks (no Docker required) ───────────────────────────────

echo "--- Static source checks (Phase 2.2-B3 wiring in RaffleActivityController) ---"

static_absent() {
  local label="$1" pattern="$2" file="$3"
  if grep -qE "$pattern" "$file" 2>/dev/null; then
    echo "  FAIL  $label: pattern still present: $pattern"
    FAIL=$((FAIL+1))
  else
    echo "  PASS  $label: pattern absent"
    PASS=$((PASS+1))
  fi
}

static_present() {
  local label="$1" pattern="$2" file="$3"
  if grep -qE "$pattern" "$file" 2>/dev/null; then
    echo "  PASS  $label: pattern present"
    PASS=$((PASS+1))
  else
    echo "  FAIL  $label: pattern not found: $pattern"
    FAIL=$((FAIL+1))
  fi
}

# creditPayExchangeSku must NOT call domain services directly
static_absent "creditPayExchangeSku no direct raffleActivityAccountQuotaService.createOrder" \
  "raffleActivityAccountQuotaService\.createOrder" "$CONTROLLER"
static_absent "creditPayExchangeSku no direct creditAdjustService.createOrder" \
  "creditAdjustService\.createOrder" "$CONTROLLER"

# Controller must inject and use write adapters
static_present "Controller injects IAccountQuotaWriteAdapter" \
  "IAccountQuotaWriteAdapter" "$CONTROLLER"
static_present "Controller injects IAccountCreditWriteAdapter" \
  "IAccountCreditWriteAdapter" "$CONTROLLER"
static_present "Controller calls accountQuotaWriteAdapter.createOrder" \
  "accountQuotaWriteAdapter\.createOrder" "$CONTROLLER"
static_present "Controller calls accountCreditWriteAdapter.createOrder" \
  "accountCreditWriteAdapter\.createOrder" "$CONTROLLER"

echo ""

# ─── Docker runtime checks ───────────────────────────────────────────────────

if ! command -v docker >/dev/null 2>&1; then
  echo "INFO: docker not found — skipping runtime checks."
  echo ""
  echo "=========================================="
  echo "Results: $PASS passed, $FAIL failed, $MANUAL manual/partial"
  echo "=========================================="
  if [ "$FAIL" -ne 0 ]; then exit 1; fi
  exit 0
fi

require_running "$SERVICE_JOB"
require_running "$SERVICE_ACCOUNT"

echo "--- Baseline health ---"
wait_health "$SERVICE_JOB" "$JOB_HEALTH" 30
wait_health "$SERVICE_ACCOUNT" "$ACCOUNT_HEALTH" 30

echo ""
echo "--- Confirm write flags default false in running containers ---"
check_flag_env "$SERVICE_JOB" "ACCOUNT_SERVICE_REMOTE_CREDIT_WRITE_ENABLED" "false"
check_flag_env "$SERVICE_JOB" "ACCOUNT_SERVICE_REMOTE_QUOTA_WRITE_ENABLED" "false"

echo ""
echo "--- Confirm local adapters loaded (not remote) with flags=false ---"
JOB_LOG_LOCAL="$(docker compose logs --since 10m "$SERVICE_JOB" 2>/dev/null | grep -E "LocalAccountCreditWriteAdapter|LocalAccountQuotaWriteAdapter" | tail -10 || true)"
if [ -n "$JOB_LOG_LOCAL" ]; then
  echo "$JOB_LOG_LOCAL"
  echo "  PASS  Local write adapter startup evidence found"
  PASS=$((PASS+1))
else
  echo "  MANUAL  No LocalAccount*WriteAdapter startup log found (Spring bean creation may not log by default). This is expected if INFO-level does not print bean names."
  MANUAL=$((MANUAL+1))
fi

echo ""
echo "--- Recreate message-job-service with BOTH write flags=true ---"
ACCOUNT_SERVICE_REMOTE_CREDIT_WRITE_ENABLED=true \
ACCOUNT_SERVICE_REMOTE_QUOTA_WRITE_ENABLED=true \
docker compose up -d --no-deps --force-recreate "$SERVICE_JOB" >/dev/null
wait_health "$SERVICE_JOB" "$JOB_HEALTH" 120

echo ""
echo "--- Confirm write flags true in recreated container ---"
check_flag_env "$SERVICE_JOB" "ACCOUNT_SERVICE_REMOTE_CREDIT_WRITE_ENABLED" "true"
check_flag_env "$SERVICE_JOB" "ACCOUNT_SERVICE_REMOTE_QUOTA_WRITE_ENABLED" "true"

echo ""
echo "--- Confirm remote adapters loaded with flags=true ---"
REMOTE_LOG="$(docker compose logs --since 5m "$SERVICE_JOB" 2>/dev/null | grep -E "AccountRemoteCreditWriteAdapter|AccountRemoteQuotaWriteAdapter" | tail -20 || true)"
if [ -n "$REMOTE_LOG" ]; then
  echo "$REMOTE_LOG"
  echo "  PASS  Remote write adapter evidence found in logs"
  PASS=$((PASS+1))
else
  echo "  MANUAL  No AccountRemote*WriteAdapter log found. Check with: docker compose logs $SERVICE_JOB | grep -E 'AccountRemote.*WriteAdapter'"
  echo "          If the adapters are loaded the bean names may only appear at DEBUG level."
  MANUAL=$((MANUAL+1))
fi

echo ""
echo "--- Confirm Dubbo is initialised when flags=true ---"
DUBBO_LOG="$(docker compose logs --since 5m "$SERVICE_JOB" 2>/dev/null | grep -iE "dubbo|nacos" | tail -10 || true)"
if [ -n "$DUBBO_LOG" ]; then
  echo "$DUBBO_LOG" | tail -5
  echo "  PASS  Dubbo/nacos log lines found in message-job-service"
  PASS=$((PASS+1))
else
  echo "  MANUAL  No Dubbo/nacos log lines found. Verify with: docker compose logs $SERVICE_JOB | grep -i dubbo"
  MANUAL=$((MANUAL+1))
fi

echo ""
echo "=========================================="
echo "Results: $PASS passed, $FAIL failed, $MANUAL manual/partial"
echo "=========================================="
echo ""
echo "NOTE: This script does NOT publish MQ messages."
echo "      Full write-path end-to-end validation requires:"
echo "      1. MQ idempotency verification per outBusinessNo"
echo "      2. Manual trigger of rebate / credit-adjust flows with both flags=true"
echo "      3. Confirmation that account-service writes to DB (not market-service)"
echo ""

if [ "$FAIL" -ne 0 ]; then
  exit 1
fi

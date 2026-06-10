#!/usr/bin/env bash
# Phase 2.3-A static validation — fulfillment-service dark launch gate.
#
# No Docker, no DB, no network required. Verifies structural correctness of
# the dark-launch setup: module exists, flags are off, Dubbo provider present,
# docker-compose wired correctly, smoke test updated, outbox safety guards intact.
#
# Usage: ./scripts/validate-fulfillment-service-b23-a.sh
# Expected: 15/15 PASS, exit 0

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PASS=0
FAIL=0

ok()  { echo "  [PASS]  $1"; PASS=$((PASS+1)); }
fail(){ echo "  [FAIL]  $1"; FAIL=$((FAIL+1)); }

check_file_exists() {
  local label="$1" path="$2"
  [ -f "$path" ] && ok "$label" || fail "$label — not found: $path"
}

check_contains() {
  local label="$1" path="$2" pattern="$3"
  if [ -f "$path" ] && grep -q "$pattern" "$path"; then
    ok "$label"
  else
    fail "$label — pattern not found: $pattern in $path"
  fi
}

check_not_contains() {
  local label="$1" path="$2" pattern="$3"
  if [ -f "$path" ] && ! grep -q "$pattern" "$path"; then
    ok "$label"
  else
    fail "$label — forbidden pattern found: $pattern in $path"
  fi
}

echo "============================================================"
echo "  Phase 2.3-A Fulfillment Service Dark Launch Validation"
echo "  $(date)"
echo "============================================================"
echo ""

# S1: pom.xml exists
check_file_exists \
  "S1:  big-market-fulfillment-service/pom.xml exists" \
  "$ROOT/big-market-fulfillment-service/pom.xml"

# S2: FulfillmentServiceApplication.java exists with correct scanBasePackages
APP_JAVA="$ROOT/big-market-fulfillment-service/src/main/java/com/dyx/market/fulfillment/FulfillmentServiceApplication.java"
if [ -f "$APP_JAVA" ] && \
   grep -q "com.dyx.market.fulfillment" "$APP_JAVA" && \
   grep -q "com.dyx.market.domain.award" "$APP_JAVA" && \
   grep -q "com.dyx.market.infrastructure" "$APP_JAVA"; then
  ok "S2:  FulfillmentServiceApplication.java exists with correct scanBasePackages"
else
  fail "S2:  FulfillmentServiceApplication.java missing or scanBasePackages incomplete"
fi

# S3: application.yml port=8087
check_contains \
  "S3:  application.yml port=8087" \
  "$ROOT/big-market-fulfillment-service/src/main/resources/application.yml" \
  "port: \${SERVER_PORT:8087}"

# S4: account.award-credit-outbox.enabled defaults false in fulfillment-service config
FULFILLMENT_YML="$ROOT/big-market-fulfillment-service/src/main/resources/application.yml"
if [ -f "$FULFILLMENT_YML" ] && \
   grep -q "award-credit-outbox" "$FULFILLMENT_YML" && \
   grep -q "ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED:false" "$FULFILLMENT_YML"; then
  ok "S4:  account.award-credit-outbox.enabled defaults false in fulfillment-service config"
else
  fail "S4:  account.award-credit-outbox.enabled not defaulted to false in fulfillment-service config"
fi

# S5: account.award-credit-outbox.enabled defaults false in message-job-service config
MSGJOB_YML="$ROOT/big-market-message-job-service/src/main/resources/application.yml"
if [ -f "$MSGJOB_YML" ] && \
   grep -q "award-credit-outbox" "$MSGJOB_YML" && \
   grep -q "ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED:false" "$MSGJOB_YML"; then
  ok "S5:  account.award-credit-outbox.enabled defaults false in message-job-service config"
else
  fail "S5:  account.award-credit-outbox.enabled not confirmed false in message-job-service config"
fi

# S6: account.award-credit-outbox.enabled defaults false in big-market-app config
APP_YML="$ROOT/big-market-app/src/main/resources/application-dev.yml"
if [ -f "$APP_YML" ] && grep -q "award-credit-outbox" "$APP_YML"; then
  if grep -A2 "award-credit-outbox:" "$APP_YML" | grep -qE "enabled.*false|false"; then
    ok "S6:  account.award-credit-outbox.enabled defaults false in big-market-app config"
  else
    fail "S6:  account.award-credit-outbox.enabled not confirmed false in big-market-app config"
  fi
else
  fail "S6:  award-credit-outbox config block not found in big-market-app application-dev.yml"
fi

# S7: FulfillmentAwardServiceRPC exists as Dubbo provider
RPC_JAVA="$ROOT/big-market-fulfillment-service/src/main/java/com/dyx/market/fulfillment/provider/FulfillmentAwardServiceRPC.java"
if [ -f "$RPC_JAVA" ] && \
   grep -q "@DubboService" "$RPC_JAVA" && \
   grep -q "IAwardService" "$RPC_JAVA"; then
  ok "S7:  FulfillmentAwardServiceRPC exists as Dubbo provider (@DubboService + IAwardService)"
else
  fail "S7:  FulfillmentAwardServiceRPC missing or not annotated with @DubboService"
fi

# S8: docker-compose.yml contains fulfillment-service entry on port 8087
COMPOSE="$ROOT/docker-compose.yml"
if [ -f "$COMPOSE" ] && \
   grep -q "big-market-fulfillment-service" "$COMPOSE" && \
   grep -q "8087:8087" "$COMPOSE"; then
  ok "S8:  docker-compose.yml contains fulfillment-service entry on port 8087"
else
  fail "S8:  docker-compose.yml missing fulfillment-service or port 8087 mapping"
fi

# S9: docker-compose.yml passes ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED to fulfillment-service
if [ -f "$COMPOSE" ] && grep -A40 "big-market-fulfillment-service:" "$COMPOSE" | grep -q "ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED"; then
  ok "S9:  docker-compose.yml passes ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED to fulfillment-service"
else
  fail "S9:  docker-compose.yml does not pass ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED to fulfillment-service"
fi

# S10: docker-compose.yml default for ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED is false
if [ -f "$COMPOSE" ] && grep "ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED" "$COMPOSE" | grep -q ":-false"; then
  ok "S10: docker-compose.yml default for ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED is false"
else
  fail "S10: docker-compose.yml ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED does not default to false"
fi

# S11: smoke-test-phase-1.sh references port 8087
SMOKE="$ROOT/scripts/smoke-test-phase-1.sh"
check_contains \
  "S11: smoke-test-phase-1.sh references port 8087 (health check added)" \
  "$SMOKE" \
  "8087"

# S12: FulfillmentAwardServiceRPC delegates to existing awardService (not a no-op)
if [ -f "$RPC_JAVA" ] && \
   grep -q "awardService.distributeAward" "$RPC_JAVA" && \
   grep -q "awardService.saveUserAwardRecord" "$RPC_JAVA"; then
  ok "S12: FulfillmentAwardServiceRPC delegates to existing AwardService (not a stub)"
else
  fail "S12: FulfillmentAwardServiceRPC does not delegate to existing AwardService"
fi

# S13: AwardRepository.saveGiveOutPrizesAggregate flag=false path still present (direct write unchanged)
AWARD_REPO="$ROOT/big-market-infrastructure/src/main/java/com/dyx/market/infrastructure/adapter/repository/AwardRepository.java"
if [ -f "$AWARD_REPO" ] && \
   grep -q "updateOrCreateCreditAccount" "$AWARD_REPO" && \
   grep -q "awardCreditOutboxEnabled" "$AWARD_REPO"; then
  ok "S13: AwardRepository flag=false path unchanged (direct user_credit_account write present)"
else
  fail "S13: AwardRepository flag=false path missing — regression risk"
fi

# S14: DispatchCreditAwardTaskJob @ConditionalOnProperty guard still present
DISPATCH_JOB="$ROOT/big-market-message-job-service/src/main/java/com/dyx/market/message/job/config/DispatchCreditAwardTaskJob.java"
if [ -f "$DISPATCH_JOB" ] && grep -q "ConditionalOnProperty" "$DISPATCH_JOB" && grep -q "havingValue.*true" "$DISPATCH_JOB"; then
  ok "S14: DispatchCreditAwardTaskJob @ConditionalOnProperty guard present (only active when flag=true)"
else
  fail "S14: DispatchCreditAwardTaskJob @ConditionalOnProperty guard missing — safety regression"
fi

# S15: No config file sets ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED default to true, and no
#      config file sets award-credit-outbox.enabled to literal true.
#      Excludes target/ build artifacts — only checks source files.
OUTBOX_ENABLED_FILES=$(find "$ROOT" -path "*/target" -prune -o \
  -name "application*.yml" -print -o -name "application*.yaml" -print 2>/dev/null | \
  xargs grep -l "award-credit-outbox" 2>/dev/null | \
  xargs grep -l "ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED:true\|award-credit-outbox.*enabled.*: *true" 2>/dev/null || true)
if [ -z "$OUTBOX_ENABLED_FILES" ]; then
  ok "S15: No config file enables account.award-credit-outbox (production gate intact)"
else
  fail "S15: award-credit-outbox is enabled in: $OUTBOX_ENABLED_FILES — PRODUCTION GATE VIOLATED"
fi

echo ""
echo "============================================================"
echo "  Results: $PASS passed, $FAIL failed  (expected 15/15)"
echo "============================================================"
[ "$FAIL" -eq 0 ] && exit 0 || exit 1

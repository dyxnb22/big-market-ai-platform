#!/usr/bin/env bash
# validate-microservices-phase-5-activity-draw-saga-outbox.sh
# Deterministic repo-only validation for Phase 5-G: draw saga/outbox design and scaffold.
#
# Checks:
#   1.  Phase 5-G design doc exists and mentions key sections
#   2.  IDrawOutboxPort interface exists in domain activity adapter port package
#   3.  IDrawOutboxPort declares publishDrawSagaStep
#   4.  DrawOutboxEvent model class exists with orderId and sagaStep fields
#   5.  LocalDrawOutboxPort implementation exists in infrastructure
#   6.  LocalDrawOutboxPort implements IDrawOutboxPort
#   7.  LocalDrawOutboxPort is annotated @ConditionalOnMissingBean
#   8.  LocalDrawOutboxPort has no @DubboReference (no remote wiring)
#   9.  IDrawOutboxPort is NOT injected into RaffleApplicationService (not wired yet)
#  10.  No activity.service.draw-outbox.enabled flag in any config
#  11.  No strategy.service.remote-decision.enabled flag exists
#  12.  No award.service.remote-fulfillment.enabled flag exists
#  13.  No draw_saga_outbox DDL in tracked SQL files (only in proposed docs)
#  14.  No DrawSagaOutboxDispatchJob introduced in activity-service
#  15.  RaffleApplicationService unchanged (still uses same three ports)
#  16.  RaffleActivityController remains in big-market-trigger
#  17.  Phase 5-F scaffold still holds (activity-service has no provider/controller/listener/job)
#  18.  Phase 5-D port (IStrategyDecisionPort + LocalStrategyDecisionPort) still intact
#  19.  Phase 5-E port (IAwardFulfillmentPort + LocalAwardFulfillmentPort) still intact
#  20.  Existing dangerous flags remain false/default-safe
#  21.  docs/evidence/generated not tracked
#  22.  No mapper XMLs moved into activity-service

set -uo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PASS=0
FAIL=0

pass() { echo "[PASS] $1"; PASS=$((PASS + 1)); }
fail() { echo "[FAIL] $1"; FAIL=$((FAIL + 1)); }

check_file() {
  local label="$1" path="$2"
  if [ -f "$ROOT/$path" ]; then
    pass "$label: $path"
  else
    fail "$label: missing $path"
  fi
}

check_not_file() {
  local label="$1" path="$2"
  if [ -f "$ROOT/$path" ]; then
    fail "$label: $path should not exist"
  else
    pass "$label: $path correctly absent"
  fi
}

check_contains() {
  local label="$1" path="$2" pattern="$3"
  if [ ! -f "$ROOT/$path" ]; then
    fail "$label: file missing $path"
    return
  fi
  if grep -qE "$pattern" "$ROOT/$path"; then
    pass "$label"
  else
    fail "$label: pattern not found in $path: $pattern"
  fi
}

check_not_contains() {
  local label="$1" path="$2" pattern="$3"
  if [ ! -f "$ROOT/$path" ]; then
    pass "$label: file $path absent (no forbidden pattern)"
    return
  fi
  if grep -qE "$pattern" "$ROOT/$path"; then
    fail "$label: forbidden pattern found in $path: $pattern"
  else
    pass "$label"
  fi
}

echo ""
echo "========================================================================"
echo "  Phase 5-G Activity Draw Saga / Outbox Scaffold Validator"
echo "  Repo: $ROOT"
echo "========================================================================"

SAGA_DOC="docs/microservices-split-phase-5-activity-draw-saga-outbox.md"
OUTBOX_PORT="big-market-domain/src/main/java/com/dyx/market/domain/activity/adapter/port/IDrawOutboxPort.java"
OUTBOX_EVENT="big-market-domain/src/main/java/com/dyx/market/domain/activity/model/event/DrawOutboxEvent.java"
LOCAL_OUTBOX="big-market-infrastructure/src/main/java/com/dyx/market/infrastructure/adapter/port/LocalDrawOutboxPort.java"
STRATEGY_PORT="big-market-domain/src/main/java/com/dyx/market/domain/activity/adapter/port/IStrategyDecisionPort.java"
LOCAL_STRATEGY="big-market-infrastructure/src/main/java/com/dyx/market/infrastructure/adapter/port/LocalStrategyDecisionPort.java"
AWARD_PORT="big-market-domain/src/main/java/com/dyx/market/domain/activity/adapter/port/IAwardFulfillmentPort.java"
LOCAL_AWARD="big-market-infrastructure/src/main/java/com/dyx/market/infrastructure/adapter/port/LocalAwardFulfillmentPort.java"
RAFFLE_SVC="big-market-domain/src/main/java/com/dyx/market/domain/activity/application/RaffleApplicationService.java"
RAFFLE_CTRL="big-market-trigger/src/main/java/com/dyx/market/trigger/http/RaffleActivityController.java"
ACT_APP="big-market-activity-service/src/main/java/com/dyx/market/activity/ActivityServiceApplication.java"
MARKET_YML="big-market-market-service/src/main/resources/application.yml"
MSGJOB_YML="big-market-message-job-service/src/main/resources/application.yml"
APP_YML="big-market-app/src/main/resources/application-dev.yml"

# -----------------------------------------------------------------------
echo ""
echo "-- [1] Phase 5-G design doc exists and covers key topics"
check_file "P5G-DOC-1 saga doc exists" "$SAGA_DOC"
check_contains "P5G-DOC-2 doc covers orchestration saga pattern" \
  "$SAGA_DOC" "[Oo]rchestration [Ss]aga|saga pattern"
check_contains "P5G-DOC-3 doc covers idempotency key (orderId)" \
  "$SAGA_DOC" "orderId|order_id.*idempoten"
check_contains "P5G-DOC-4 doc covers IDrawOutboxPort" \
  "$SAGA_DOC" "IDrawOutboxPort"
check_contains "P5G-DOC-5 doc covers compensation/rollback" \
  "$SAGA_DOC" "[Cc]ompensati|[Rr]ollback"
check_contains "P5G-DOC-6 doc references Phase 7-D DDL precondition" \
  "$SAGA_DOC" "Phase 7-D|7-D"
check_contains "P5G-DOC-7 doc documents non-goals section" \
  "$SAGA_DOC" "Non-Goals|non-goals|[Nn]on.Goals"
check_contains "P5G-DOC-8 doc states IDrawOutboxPort not wired into RaffleApplicationService" \
  "$SAGA_DOC" "NOT.*wired|not.*wired|not yet.*inject"

# -----------------------------------------------------------------------
echo ""
echo "-- [2] IDrawOutboxPort interface exists"
check_file "P5G-PORT-1 IDrawOutboxPort interface" "$OUTBOX_PORT"

# -----------------------------------------------------------------------
echo ""
echo "-- [3] IDrawOutboxPort declares publishDrawSagaStep"
check_contains "P5G-PORT-2 publishDrawSagaStep declared" \
  "$OUTBOX_PORT" "publishDrawSagaStep"

# -----------------------------------------------------------------------
echo ""
echo "-- [4] DrawOutboxEvent model exists with orderId and sagaStep"
check_file "P5G-EVT-1 DrawOutboxEvent model" "$OUTBOX_EVENT"
check_contains "P5G-EVT-2 orderId field" "$OUTBOX_EVENT" "orderId"
check_contains "P5G-EVT-3 sagaStep field or enum" "$OUTBOX_EVENT" "sagaStep|DrawSagaStep"

# -----------------------------------------------------------------------
echo ""
echo "-- [5] LocalDrawOutboxPort implementation exists"
check_file "P5G-IMPL-1 LocalDrawOutboxPort" "$LOCAL_OUTBOX"

# -----------------------------------------------------------------------
echo ""
echo "-- [6] LocalDrawOutboxPort implements IDrawOutboxPort"
check_contains "P5G-IMPL-2 implements IDrawOutboxPort" \
  "$LOCAL_OUTBOX" "implements IDrawOutboxPort"

# -----------------------------------------------------------------------
echo ""
echo "-- [7] LocalDrawOutboxPort annotated @ConditionalOnMissingBean"
check_contains "P5G-IMPL-3 @ConditionalOnMissingBean" \
  "$LOCAL_OUTBOX" "@ConditionalOnMissingBean"

# -----------------------------------------------------------------------
echo ""
echo "-- [8] LocalDrawOutboxPort has no @DubboReference (no remote wiring)"
check_not_contains "P5G-IMPL-4 no @DubboReference" "$LOCAL_OUTBOX" "@DubboReference"

# -----------------------------------------------------------------------
echo ""
echo "-- [9] IDrawOutboxPort NOT injected into RaffleApplicationService (scaffold only)"
check_not_contains "P5G-SVC-1 IDrawOutboxPort not in RaffleApplicationService imports" \
  "$RAFFLE_SVC" "import.*IDrawOutboxPort"
check_not_contains "P5G-SVC-2 drawOutboxPort not injected in RaffleApplicationService" \
  "$RAFFLE_SVC" "IDrawOutboxPort drawOutboxPort|drawOutboxPort"

# -----------------------------------------------------------------------
echo ""
echo "-- [10] No activity.service.draw-outbox.enabled flag in any config"
DRAW_OUTBOX_FLAG=$(find "$ROOT" -name "application*.yml" -not -path "*/target/*" \
  -exec grep -lE "^[^#]*activity\.service\.draw-outbox\.enabled" {} + 2>/dev/null | wc -l | tr -d ' ')
if [ "$DRAW_OUTBOX_FLAG" = "0" ]; then
  pass "P5G-FLAG-1 no activity.service.draw-outbox.enabled YAML property"
else
  fail "P5G-FLAG-1 activity.service.draw-outbox.enabled found in $DRAW_OUTBOX_FLAG yml file(s)"
fi

# -----------------------------------------------------------------------
echo ""
echo "-- [11] No strategy.service.remote-decision.enabled flag"
DECISION_FLAG=$(find "$ROOT" -name "application*.yml" -not -path "*/target/*" \
  -exec grep -lE "^[^#]*strategy\.service\.remote-decision\.enabled" {} + 2>/dev/null | wc -l | tr -d ' ')
if [ "$DECISION_FLAG" = "0" ]; then
  pass "P5G-FLAG-2 no strategy.service.remote-decision.enabled YAML property"
else
  fail "P5G-FLAG-2 strategy.service.remote-decision.enabled found in $DECISION_FLAG yml file(s)"
fi

# -----------------------------------------------------------------------
echo ""
echo "-- [12] No award.service.remote-fulfillment.enabled flag"
FULFILLMENT_FLAG=$(find "$ROOT" -name "application*.yml" -not -path "*/target/*" \
  -exec grep -lE "^[^#]*award\.service\.remote-fulfillment\.enabled" {} + 2>/dev/null | wc -l | tr -d ' ')
if [ "$FULFILLMENT_FLAG" = "0" ]; then
  pass "P5G-FLAG-3 no award.service.remote-fulfillment.enabled YAML property"
else
  fail "P5G-FLAG-3 award.service.remote-fulfillment.enabled found in $FULFILLMENT_FLAG yml file(s)"
fi

# -----------------------------------------------------------------------
echo ""
echo "-- [13] No draw_saga_outbox DDL in tracked SQL or migration files (should be proposed-only)"
DDL_COUNT=$(find "$ROOT" -name "*.sql" -not -path "*/target/*" -not -path "*/proposed-*" \
  -exec grep -lE "draw_saga_outbox|CREATE TABLE.*draw_saga" {} + 2>/dev/null | wc -l | tr -d ' ')
if [ "$DDL_COUNT" = "0" ]; then
  pass "P5G-DDL-1 no draw_saga_outbox DDL in non-proposed SQL files"
else
  fail "P5G-DDL-1 draw_saga_outbox DDL found in non-proposed SQL files ($DDL_COUNT file(s))"
fi

# -----------------------------------------------------------------------
echo ""
echo "-- [14] No DrawSagaOutboxDispatchJob in activity-service"
DISPATCH_JOB=$(find "$ROOT/big-market-activity-service/src" -type f -name "*.java" \
  -exec grep -l "DrawSagaOutboxDispatchJob\|@XxlJob" {} + 2>/dev/null | wc -l | tr -d ' ')
if [ "$DISPATCH_JOB" = "0" ]; then
  pass "P5G-JOB-1 no DrawSagaOutboxDispatchJob or @XxlJob in activity-service"
else
  fail "P5G-JOB-1 unexpected @XxlJob or saga dispatch job in activity-service ($DISPATCH_JOB file(s))"
fi

# -----------------------------------------------------------------------
echo ""
echo "-- [15] RaffleApplicationService still uses IStrategyDecisionPort and IAwardFulfillmentPort"
check_contains "P5G-SVC-3 uses IStrategyDecisionPort" "$RAFFLE_SVC" "IStrategyDecisionPort"
check_contains "P5G-SVC-4 uses IAwardFulfillmentPort" "$RAFFLE_SVC" "IAwardFulfillmentPort"
check_contains "P5G-SVC-5 calls strategyDecisionPort.performRaffle" \
  "$RAFFLE_SVC" "strategyDecisionPort\.performRaffle"
check_contains "P5G-SVC-6 calls awardFulfillmentPort.saveUserAwardRecord" \
  "$RAFFLE_SVC" "awardFulfillmentPort\.saveUserAwardRecord"

# -----------------------------------------------------------------------
echo ""
echo "-- [16] RaffleActivityController remains in big-market-trigger (not moved)"
check_file "P5G-CTRL-1 RaffleActivityController in trigger" "$RAFFLE_CTRL"
ACT_CTRL=$(find "$ROOT/big-market-activity-service/src" -type f \
  -name "RaffleActivityController.java" 2>/dev/null | wc -l | tr -d ' ')
if [ "$ACT_CTRL" = "0" ]; then
  pass "P5G-CTRL-2 RaffleActivityController not moved to activity-service"
else
  fail "P5G-CTRL-2 RaffleActivityController incorrectly moved to activity-service"
fi

# -----------------------------------------------------------------------
echo ""
echo "-- [17] Phase 5-F scaffold still holds (activity-service has no provider/controller/listener/job)"
DUBBO_SVC=$(find "$ROOT/big-market-activity-service/src" -type f -name "*.java" \
  -exec grep -l "@DubboService" {} + 2>/dev/null | wc -l | tr -d ' ')
if [ "$DUBBO_SVC" = "0" ]; then
  pass "P5G-MOD-1 no @DubboService in activity-service (Phase 5-F boundary holds)"
else
  fail "P5G-MOD-1 unexpected @DubboService in activity-service ($DUBBO_SVC file(s))"
fi
REST_CTRL=$(find "$ROOT/big-market-activity-service/src" -type f -name "*.java" \
  -exec grep -l "@RestController" {} + 2>/dev/null | wc -l | tr -d ' ')
if [ "$REST_CTRL" = "0" ]; then
  pass "P5G-MOD-2 no @RestController in activity-service (Phase 5-F boundary holds)"
else
  fail "P5G-MOD-2 unexpected @RestController in activity-service ($REST_CTRL file(s))"
fi
MQ_LISTENER=$(find "$ROOT/big-market-activity-service/src" -type f -name "*.java" \
  -exec grep -l "@RabbitListener" {} + 2>/dev/null | wc -l | tr -d ' ')
if [ "$MQ_LISTENER" = "0" ]; then
  pass "P5G-MOD-3 no @RabbitListener in activity-service (Phase 5-F boundary holds)"
else
  fail "P5G-MOD-3 unexpected @RabbitListener in activity-service ($MQ_LISTENER file(s))"
fi

# -----------------------------------------------------------------------
echo ""
echo "-- [18] Phase 5-D port (IStrategyDecisionPort + LocalStrategyDecisionPort) still intact"
check_file "P5G-P5D-1 IStrategyDecisionPort exists" "$STRATEGY_PORT"
check_file "P5G-P5D-2 LocalStrategyDecisionPort exists" "$LOCAL_STRATEGY"
check_contains "P5G-P5D-3 LocalStrategyDecisionPort implements interface" \
  "$LOCAL_STRATEGY" "implements IStrategyDecisionPort"
check_contains "P5G-P5D-4 LocalStrategyDecisionPort delegates to IRaffleStrategy" \
  "$LOCAL_STRATEGY" "IRaffleStrategy"

# -----------------------------------------------------------------------
echo ""
echo "-- [19] Phase 5-E port (IAwardFulfillmentPort + LocalAwardFulfillmentPort) still intact"
check_file "P5G-P5E-1 IAwardFulfillmentPort exists" "$AWARD_PORT"
check_file "P5G-P5E-2 LocalAwardFulfillmentPort exists" "$LOCAL_AWARD"
check_contains "P5G-P5E-3 LocalAwardFulfillmentPort implements interface" \
  "$LOCAL_AWARD" "implements IAwardFulfillmentPort"
check_contains "P5G-P5E-4 LocalAwardFulfillmentPort delegates to IAwardService" \
  "$LOCAL_AWARD" "IAwardService"

# -----------------------------------------------------------------------
echo ""
echo "-- [20] Existing dangerous flags remain false/default-safe"
for cfg in "$MARKET_YML" "$MSGJOB_YML" "$APP_YML"; do
  rel="${cfg#$ROOT/}"
  if [ -f "$ROOT/$rel" ]; then
    for flag in \
      "ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED" \
      "ACCOUNT_FULFILLMENT_REMOTE_AWARD_ENABLED" \
      "ACCOUNT_SERVICE_REMOTE_QUOTA_DECREMENT_ENABLED" \
      "REBATE_SERVICE_REMOTE_CREATE_ORDER_ENABLED" \
      "REBATE_SERVICE_REMOTE_READ_ENABLED" \
      "STRATEGY_SERVICE_REMOTE_READ_ENABLED" \
      "STRATEGY_SERVICE_REMOTE_DECISION_ENABLED" \
      "AWARD_SERVICE_REMOTE_FULFILLMENT_ENABLED" \
      "ACTIVITY_SERVICE_DRAW_OUTBOX_ENABLED"; do
      if grep -qE "${flag}(:-|=|: *)true" "$ROOT/$rel"; then
        fail "P5G-SAFEFLAG: $flag is true in $rel"
      else
        pass "P5G-SAFEFLAG: $flag not true in $rel"
      fi
    done
  fi
done

# -----------------------------------------------------------------------
echo ""
echo "-- [21] docs/evidence/generated not tracked"
if git -C "$ROOT" ls-files "docs/evidence/generated" 2>/dev/null | grep -q .; then
  fail "P5G-EVID: docs/evidence/generated is tracked by git"
else
  pass "P5G-EVID: docs/evidence/generated not tracked"
fi

# -----------------------------------------------------------------------
echo ""
echo "-- [22] No mapper XMLs moved into activity-service"
MAPPER_XML=$(find "$ROOT/big-market-activity-service/src/main/resources/mybatis/mapper" \
  -type f -name "*.xml" 2>/dev/null | wc -l | tr -d ' ')
if [ "$MAPPER_XML" = "0" ]; then
  pass "P5G-MAP-1 no mapper XMLs in activity-service (Phase 5-F boundary holds)"
else
  fail "P5G-MAP-1 found $MAPPER_XML mapper XML(s) in activity-service (should be 0)"
fi

# -----------------------------------------------------------------------
echo ""
echo "========================================================================"
echo "  SUMMARY"
echo "========================================================================"
echo "Checks passed: $PASS"
echo "Checks failed: $FAIL"

if [ "$FAIL" -eq 0 ]; then
  echo "RESULT: PASS — Phase 5-G draw saga/outbox scaffold is repo-ready."
  echo "        IDrawOutboxPort + LocalDrawOutboxPort + DrawOutboxEvent introduced."
  echo "        Design doc committed. No draw execution moved. No traffic enabled."
  echo "        No remote flags introduced. Phase 5-D/E/F boundaries intact."
  echo "        Recommended next batch: Phase 6-A (DAO ownership matrix)."
  exit 0
else
  echo "RESULT: FAIL — $FAIL check(s) failed. Fix before tagging."
  exit 1
fi

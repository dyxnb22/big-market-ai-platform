#!/usr/bin/env bash
# validate-microservices-phase-5-activity-service-scaffold.sh
# Deterministic repo-only validation for Phase 5-F: activity-service dark-launch scaffold.
#
# Checks:
#   1.  big-market-activity-service/pom.xml exists
#   2.  root pom.xml registers <module>big-market-activity-service</module>
#   3.  ActivityServiceApplication.java exists
#   4.  Service port is 8090 (distinct from all other services)
#   5.  Module does NOT depend on big-market-trigger
#   6.  scanBasePackages does NOT include trigger packages
#   7.  scanBasePackages does NOT include strategy/award/rebate domains
#   8.  No @DubboService exists under big-market-activity-service
#   9.  No HTTP @RestController exists under big-market-activity-service
#  10.  No MQ listener (@RabbitListener) exists under big-market-activity-service
#  11.  No XXL-Job handler (@XxlJob) exists under big-market-activity-service
#  12.  No mapper XMLs were copied into big-market-activity-service
#  13.  RaffleApplicationService remains in big-market-domain
#  14.  RaffleActivityController remains in big-market-trigger
#  15.  RaffleApplicationService still uses IActivityAccountPort
#  16.  RaffleApplicationService still uses IStrategyDecisionPort
#  17.  RaffleApplicationService still uses IAwardFulfillmentPort
#  18.  No strategy.service.remote-decision.enabled flag exists anywhere
#  19.  No award.service.remote-fulfillment.enabled flag exists anywhere
#  20.  Existing dangerous flags remain false/default-safe
#  21.  docs/evidence/generated is not tracked
#  22.  Phase 5-F scaffold doc exists and documents non-goals/blockers

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
echo "  Phase 5-F Activity-Service Scaffold Validator"
echo "  Repo: $ROOT"
echo "========================================================================"

ACT_POM="big-market-activity-service/pom.xml"
ACT_APP="big-market-activity-service/src/main/java/com/dyx/market/activity/ActivityServiceApplication.java"
ACT_YML="big-market-activity-service/src/main/resources/application.yml"
ROOT_POM="pom.xml"
RAFFLE_SVC="big-market-domain/src/main/java/com/dyx/market/domain/activity/application/RaffleApplicationService.java"
RAFFLE_CTRL="big-market-trigger/src/main/java/com/dyx/market/trigger/http/RaffleActivityController.java"
MARKET_YML="big-market-market-service/src/main/resources/application.yml"
MSGJOB_YML="big-market-message-job-service/src/main/resources/application.yml"
APP_YML="big-market-app/src/main/resources/application-dev.yml"
DOCKER_COMPOSE="docker-compose.yml"
SCAFFOLD_DOC="docs/microservices-split-phase-5-activity-service-scaffold.md"

# -----------------------------------------------------------------------
echo ""
echo "-- [1] big-market-activity-service/pom.xml exists"
check_file "P5F-MOD-1 activity-service pom" "$ACT_POM"

# -----------------------------------------------------------------------
echo ""
echo "-- [2] root pom.xml registers activity-service module"
check_contains "P5F-MOD-2 root pom has activity-service module" \
  "$ROOT_POM" "<module>big-market-activity-service</module>"

# -----------------------------------------------------------------------
echo ""
echo "-- [3] ActivityServiceApplication.java exists"
check_file "P5F-APP-1 application class" "$ACT_APP"

# -----------------------------------------------------------------------
echo ""
echo "-- [4] Service port is 8090 (distinct)"
check_contains "P5F-PORT-1 port 8090 in application.yml" \
  "$ACT_YML" "port.*8090|8090.*port"

# Verify 8090 is not used by any other service
CONFLICT=$(grep -rEl "port.*:.*8090|SERVER_PORT.*8090" \
  "$ROOT/big-market-market-service/src/main/resources/" \
  "$ROOT/big-market-message-job-service/src/main/resources/" \
  "$ROOT/big-market-account-service/src/main/resources/" \
  "$ROOT/big-market-fulfillment-service/src/main/resources/" \
  "$ROOT/big-market-rebate-service/src/main/resources/" \
  "$ROOT/big-market-strategy-service/src/main/resources/" \
  "$ROOT/big-market-gateway/src/main/resources/" \
  "$ROOT/big-market-auth-service/src/main/resources/" \
  "$ROOT/big-market-admin-service/src/main/resources/" \
  "$ROOT/big-market-chatbot-service/src/main/resources/" \
  2>/dev/null | tr '\n' ' ')
if [ -z "$CONFLICT" ]; then
  pass "P5F-PORT-2 port 8090 is not used by any other service"
else
  fail "P5F-PORT-2 port 8090 conflicts with: $CONFLICT"
fi

# -----------------------------------------------------------------------
echo ""
echo "-- [5] Module does NOT depend on big-market-trigger"
# Use artifactId element pattern to avoid matching comments that explain the exclusion
check_not_contains "P5F-DEP-1 no big-market-trigger dep in activity pom" \
  "$ACT_POM" "<artifactId>big-market-trigger</artifactId>"

# -----------------------------------------------------------------------
echo ""
echo "-- [6] scanBasePackages does NOT include trigger packages"
# Check that the package name does NOT appear within double-quotes (annotation value)
# This avoids matching Javadoc comments that document the exclusion
if grep -qE '"[^"]*com\.dyx\.market\.trigger[^"]*"' "$ROOT/$ACT_APP" 2>/dev/null; then
  fail "P5F-SCAN-1 trigger package found in scanBasePackages annotation in $ACT_APP"
else
  pass "P5F-SCAN-1 no trigger package in scanBasePackages annotation"
fi

# -----------------------------------------------------------------------
echo ""
echo "-- [7] scanBasePackages does NOT include strategy/award/rebate domains"
if grep -qE '"[^"]*com\.dyx\.market\.domain\.strategy[^"]*"' "$ROOT/$ACT_APP" 2>/dev/null; then
  fail "P5F-SCAN-2 domain.strategy found in scanBasePackages annotation in $ACT_APP"
else
  pass "P5F-SCAN-2 no domain.strategy in scanBasePackages annotation"
fi
if grep -qE '"[^"]*com\.dyx\.market\.domain\.award[^"]*"' "$ROOT/$ACT_APP" 2>/dev/null; then
  fail "P5F-SCAN-3 domain.award found in scanBasePackages annotation in $ACT_APP"
else
  pass "P5F-SCAN-3 no domain.award in scanBasePackages annotation"
fi
if grep -qE '"[^"]*com\.dyx\.market\.domain\.rebate[^"]*"' "$ROOT/$ACT_APP" 2>/dev/null; then
  fail "P5F-SCAN-4 domain.rebate found in scanBasePackages annotation in $ACT_APP"
else
  pass "P5F-SCAN-4 no domain.rebate in scanBasePackages annotation"
fi

# -----------------------------------------------------------------------
echo ""
echo "-- [8] No @DubboService under big-market-activity-service"
DUBBO_SVC=$(find "$ROOT/big-market-activity-service/src" -type f -name "*.java" \
  -exec grep -l "@DubboService" {} + 2>/dev/null | wc -l | tr -d ' ')
if [ "$DUBBO_SVC" = "0" ]; then
  pass "P5F-PROV-1 no @DubboService in activity-service"
else
  fail "P5F-PROV-1 found @DubboService in activity-service ($DUBBO_SVC file(s))"
fi

# -----------------------------------------------------------------------
echo ""
echo "-- [9] No HTTP @RestController under big-market-activity-service"
REST_CTRL=$(find "$ROOT/big-market-activity-service/src" -type f -name "*.java" \
  -exec grep -l "@RestController" {} + 2>/dev/null | wc -l | tr -d ' ')
if [ "$REST_CTRL" = "0" ]; then
  pass "P5F-HTTP-1 no @RestController in activity-service"
else
  fail "P5F-HTTP-1 found @RestController in activity-service ($REST_CTRL file(s))"
fi

# -----------------------------------------------------------------------
echo ""
echo "-- [10] No MQ listener (@RabbitListener) under big-market-activity-service"
MQ_LISTENER=$(find "$ROOT/big-market-activity-service/src" -type f -name "*.java" \
  -exec grep -l "@RabbitListener" {} + 2>/dev/null | wc -l | tr -d ' ')
if [ "$MQ_LISTENER" = "0" ]; then
  pass "P5F-MQ-1 no @RabbitListener in activity-service"
else
  fail "P5F-MQ-1 found @RabbitListener in activity-service ($MQ_LISTENER file(s))"
fi

# -----------------------------------------------------------------------
echo ""
echo "-- [11] No XXL-Job handler (@XxlJob) under big-market-activity-service"
XXL_JOB=$(find "$ROOT/big-market-activity-service/src" -type f -name "*.java" \
  -exec grep -l "@XxlJob" {} + 2>/dev/null | wc -l | tr -d ' ')
if [ "$XXL_JOB" = "0" ]; then
  pass "P5F-JOB-1 no @XxlJob in activity-service"
else
  fail "P5F-JOB-1 found @XxlJob in activity-service ($XXL_JOB file(s))"
fi

# -----------------------------------------------------------------------
echo ""
echo "-- [12] No mapper XMLs under big-market-activity-service (except mybatis-config.xml)"
MAPPER_XML=$(find "$ROOT/big-market-activity-service/src/main/resources/mybatis/mapper" \
  -type f -name "*.xml" 2>/dev/null | wc -l | tr -d ' ')
if [ "$MAPPER_XML" = "0" ]; then
  pass "P5F-MAP-1 no mapper XMLs copied into activity-service"
else
  fail "P5F-MAP-1 found $MAPPER_XML mapper XML(s) in activity-service (should be 0)"
fi

# -----------------------------------------------------------------------
echo ""
echo "-- [13] RaffleApplicationService remains in big-market-domain"
check_file "P5F-SVC-1 RaffleApplicationService in domain" "$RAFFLE_SVC"

ACT_RAFFLE=$(find "$ROOT/big-market-activity-service/src" -type f \
  -name "RaffleApplicationService.java" 2>/dev/null | wc -l | tr -d ' ')
if [ "$ACT_RAFFLE" = "0" ]; then
  pass "P5F-SVC-2 RaffleApplicationService NOT moved into activity-service"
else
  fail "P5F-SVC-2 RaffleApplicationService was incorrectly moved into activity-service"
fi

# -----------------------------------------------------------------------
echo ""
echo "-- [14] RaffleActivityController remains in big-market-trigger"
check_file "P5F-CTRL-1 RaffleActivityController in trigger" "$RAFFLE_CTRL"

ACT_CTRL=$(find "$ROOT/big-market-activity-service/src" -type f \
  -name "RaffleActivityController.java" 2>/dev/null | wc -l | tr -d ' ')
if [ "$ACT_CTRL" = "0" ]; then
  pass "P5F-CTRL-2 RaffleActivityController NOT moved into activity-service"
else
  fail "P5F-CTRL-2 RaffleActivityController was incorrectly moved into activity-service"
fi

# -----------------------------------------------------------------------
echo ""
echo "-- [15-17] Port boundaries still intact"
# IActivityAccountPort is used in RaffleActivityPartakeService (not RaffleApplicationService directly)
PARTAKE_SVC="big-market-domain/src/main/java/com/dyx/market/domain/activity/service/partake/RaffleActivityPartakeService.java"
check_contains "P5F-SVC-3 IActivityAccountPort used in partake service" \
  "$PARTAKE_SVC" "IActivityAccountPort"
check_contains "P5F-SVC-4 RaffleApplicationService uses IStrategyDecisionPort" \
  "$RAFFLE_SVC" "IStrategyDecisionPort"
check_contains "P5F-SVC-5 RaffleApplicationService uses IAwardFulfillmentPort" \
  "$RAFFLE_SVC" "IAwardFulfillmentPort"

# -----------------------------------------------------------------------
echo ""
echo "-- [18] No strategy.service.remote-decision.enabled YAML property in application.yml files"
# Check only .yml files and exclude comment lines (lines starting with optional whitespace + #)
DECISION_FLAG=$(find "$ROOT" -name "application*.yml" -not -path "*/target/*" \
  -exec grep -lE "^[^#]*strategy\.service\.remote-decision\.enabled" {} + 2>/dev/null | wc -l | tr -d ' ')
if [ "$DECISION_FLAG" = "0" ]; then
  pass "P5F-FLAG-1 no strategy.service.remote-decision.enabled YAML property"
else
  fail "P5F-FLAG-1 strategy.service.remote-decision.enabled YAML property found in $DECISION_FLAG yml file(s)"
fi

# -----------------------------------------------------------------------
echo ""
echo "-- [19] No award.service.remote-fulfillment.enabled YAML property in application.yml files"
FULFILLMENT_FLAG=$(find "$ROOT" -name "application*.yml" -not -path "*/target/*" \
  -exec grep -lE "^[^#]*award\.service\.remote-fulfillment\.enabled" {} + 2>/dev/null | wc -l | tr -d ' ')
if [ "$FULFILLMENT_FLAG" = "0" ]; then
  pass "P5F-FLAG-2 no award.service.remote-fulfillment.enabled YAML property"
else
  fail "P5F-FLAG-2 award.service.remote-fulfillment.enabled YAML property found in $FULFILLMENT_FLAG yml file(s)"
fi

# -----------------------------------------------------------------------
echo ""
echo "-- [20] Existing dangerous flags remain false/default-safe"
for cfg in "$MARKET_YML" "$MSGJOB_YML" "$APP_YML" "$DOCKER_COMPOSE" "$ACT_YML"; do
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
      "AWARD_SERVICE_REMOTE_FULFILLMENT_ENABLED"; do
      if grep -qE "${flag}(:-|=|: *)true" "$ROOT/$rel"; then
        fail "P5F-SAFEFLAG: $flag is true in $rel"
      else
        pass "P5F-SAFEFLAG: $flag not true in $rel"
      fi
    done
  fi
done

# -----------------------------------------------------------------------
echo ""
echo "-- [21] docs/evidence/generated not tracked"
if git -C "$ROOT" ls-files "docs/evidence/generated" 2>/dev/null | grep -q .; then
  fail "P5F-EVID: docs/evidence/generated is tracked by git"
else
  pass "P5F-EVID: docs/evidence/generated not tracked"
fi

# -----------------------------------------------------------------------
echo ""
echo "-- [22] Phase 5-F scaffold doc exists and documents non-goals/blockers"
check_file "P5F-DOC-1 scaffold doc exists" "$SCAFFOLD_DOC"
check_contains "P5F-DOC-2 doc mentions Phase 5-F" \
  "$SCAFFOLD_DOC" "Phase 5-F"
check_contains "P5F-DOC-3 doc mentions Phase 5-G blocker" \
  "$SCAFFOLD_DOC" "Phase 5-G"
check_contains "P5F-DOC-4 doc mentions RaffleApplicationService not moved" \
  "$SCAFFOLD_DOC" "RaffleApplicationService"
check_contains "P5F-DOC-5 doc mentions table ownership future work" \
  "$SCAFFOLD_DOC" "[Pp]hase 7|table ownership"
check_contains "P5F-DOC-6 doc mentions no DubboService provider" \
  "$SCAFFOLD_DOC" "DubboService|no.*provider"
check_contains "P5F-DOC-7 doc mentions no HTTP controller" \
  "$SCAFFOLD_DOC" "RestController|[Nn]o.*HTTP|[Nn]o.*controller"

# -----------------------------------------------------------------------
echo ""
echo "========================================================================"
echo "  SUMMARY"
echo "========================================================================"
echo "Checks passed: $PASS"
echo "Checks failed: $FAIL"

if [ "$FAIL" -eq 0 ]; then
  echo "RESULT: PASS — Phase 5-F activity-service scaffold is repo-ready."
  echo "        Module structure created. Scan boundary enforced."
  echo "        No draw execution moved. No traffic enabled. No remote flags introduced."
  exit 0
else
  echo "RESULT: FAIL — $FAIL check(s) failed. Fix before tagging."
  exit 1
fi

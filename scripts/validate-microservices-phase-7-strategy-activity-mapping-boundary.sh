#!/usr/bin/env bash
# validate-microservices-phase-7-strategy-activity-mapping-boundary.sh
#
# Phase 7-A validator: strategy–activity ID-mapping boundary (AL-1).
#
# Asserts that:
#   AL-1 (StrategyRepository -> IRaffleActivityDao) has been resolved by routing
#        activityId <-> strategyId lookups through IStrategyActivityMappingPort.
#
# Hard constraints verified:
#   1. Design doc exists: docs/microservices-split-phase-7-strategy-activity-mapping-boundary.md
#   2. StrategyRepository no longer imports IRaffleActivityDao.
#   3. IStrategyActivityMappingPort declares both mapping methods.
#   4. LocalStrategyActivityMappingPort implements both methods and delegates to IRaffleActivityDao.
#   5. AL-2/AL-3/AL-4 remain resolved (spot-check).
#   6. No new forbidden DAO imports in StrategyRepository.
#   7. activity-service still has no mapper XMLs, controllers, providers, listeners, jobs.
#   8. Remote flags remain disabled.
#   9. Phase 6-B validator passes.

set -uo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PASS=0
FAIL=0

pass() { echo "[PASS] $*"; PASS=$((PASS + 1)); }
fail() { echo "[FAIL] $*"; FAIL=$((FAIL + 1)); }

INFRA_REPO="$REPO_ROOT/big-market-infrastructure/src/main/java/com/dyx/market/infrastructure/adapter/repository"
INFRA_PORT="$REPO_ROOT/big-market-infrastructure/src/main/java/com/dyx/market/infrastructure/adapter/port"
DOMAIN_STRATEGY_PORT="$REPO_ROOT/big-market-domain/src/main/java/com/dyx/market/domain/strategy/adapter/port"

echo ""
echo "========================================================================"
echo "  Phase 7-A: Strategy–Activity Mapping Boundary Validator (AL-1)"
echo "  Repo: $REPO_ROOT"
echo "========================================================================"

# ── 1. Design doc exists ──────────────────────────────────────────────────────
echo ""
echo "── 1. Design doc presence ──"

DESIGN_DOC="$REPO_ROOT/docs/microservices-split-phase-7-strategy-activity-mapping-boundary.md"
if [[ -f "$DESIGN_DOC" ]]; then
  pass "Design doc exists: docs/microservices-split-phase-7-strategy-activity-mapping-boundary.md"
else
  fail "Design doc missing: docs/microservices-split-phase-7-strategy-activity-mapping-boundary.md"
fi

# Confirm the doc captures the key design decisions
for keyword in "IStrategyActivityMappingPort" "IRaffleActivityDao" "AL-1" "raffle_activity" "activity-service"; do
  if grep -q "$keyword" "$DESIGN_DOC" 2>/dev/null; then
    pass "Design doc mentions: $keyword"
  else
    fail "Design doc missing keyword: $keyword"
  fi
done

# ── 2. AL-1 resolved: StrategyRepository must not import IRaffleActivityDao ───
echo ""
echo "── 2. AL-1 resolved — StrategyRepository direct activity-DAO access removed ──"

STRATEGY_REPO="$INFRA_REPO/StrategyRepository.java"
if [[ ! -f "$STRATEGY_REPO" ]]; then
  fail "StrategyRepository.java not found: $STRATEGY_REPO"
else
  if grep -q "IRaffleActivityDao" "$STRATEGY_REPO" 2>/dev/null; then
    fail "StrategyRepository still references IRaffleActivityDao — AL-1 not resolved"
  else
    pass "StrategyRepository does not reference IRaffleActivityDao (AL-1 resolved)"
  fi

  if grep -q "raffleActivityDao\b" "$STRATEGY_REPO" 2>/dev/null; then
    fail "StrategyRepository still has raffleActivityDao field — AL-1 not fully removed"
  else
    pass "StrategyRepository has no raffleActivityDao field"
  fi

  if grep -q "IStrategyActivityMappingPort" "$STRATEGY_REPO" 2>/dev/null; then
    pass "StrategyRepository injects IStrategyActivityMappingPort (port seam wired)"
  else
    fail "StrategyRepository does not inject IStrategyActivityMappingPort — port seam missing"
  fi

  if grep -q "strategyActivityMappingPort.queryStrategyIdByActivityId\|strategyActivityMappingPort\.queryStrategyIdByActivityId" "$STRATEGY_REPO" 2>/dev/null; then
    pass "StrategyRepository.queryStrategyIdByActivityId delegates to strategyActivityMappingPort"
  else
    fail "StrategyRepository.queryStrategyIdByActivityId does not delegate to strategyActivityMappingPort"
  fi

  if grep -q "strategyActivityMappingPort.queryActivityIdByStrategyId\|strategyActivityMappingPort\.queryActivityIdByStrategyId" "$STRATEGY_REPO" 2>/dev/null; then
    pass "StrategyRepository quota methods delegate to strategyActivityMappingPort.queryActivityIdByStrategyId"
  else
    fail "StrategyRepository quota methods do not call strategyActivityMappingPort.queryActivityIdByStrategyId"
  fi
fi

# ── 3. IStrategyActivityMappingPort declares both methods ─────────────────────
echo ""
echo "── 3. IStrategyActivityMappingPort interface ──"

MAPPING_PORT_IFACE="$DOMAIN_STRATEGY_PORT/IStrategyActivityMappingPort.java"
if [[ ! -f "$MAPPING_PORT_IFACE" ]]; then
  fail "IStrategyActivityMappingPort.java not found: $MAPPING_PORT_IFACE"
else
  if grep -q "queryStrategyIdByActivityId" "$MAPPING_PORT_IFACE" 2>/dev/null; then
    pass "IStrategyActivityMappingPort declares queryStrategyIdByActivityId"
  else
    fail "IStrategyActivityMappingPort does not declare queryStrategyIdByActivityId"
  fi

  if grep -q "queryActivityIdByStrategyId" "$MAPPING_PORT_IFACE" 2>/dev/null; then
    pass "IStrategyActivityMappingPort declares queryActivityIdByStrategyId"
  else
    fail "IStrategyActivityMappingPort does not declare queryActivityIdByStrategyId"
  fi
fi

# ── 4. LocalStrategyActivityMappingPort delegates to IRaffleActivityDao ────────
echo ""
echo "── 4. LocalStrategyActivityMappingPort local implementation ──"

LOCAL_MAPPING_PORT="$INFRA_PORT/LocalStrategyActivityMappingPort.java"
if [[ ! -f "$LOCAL_MAPPING_PORT" ]]; then
  fail "LocalStrategyActivityMappingPort.java not found: $LOCAL_MAPPING_PORT"
else
  if grep -q "queryStrategyIdByActivityId" "$LOCAL_MAPPING_PORT" 2>/dev/null; then
    pass "LocalStrategyActivityMappingPort implements queryStrategyIdByActivityId"
  else
    fail "LocalStrategyActivityMappingPort does not implement queryStrategyIdByActivityId"
  fi

  if grep -q "queryActivityIdByStrategyId" "$LOCAL_MAPPING_PORT" 2>/dev/null; then
    pass "LocalStrategyActivityMappingPort implements queryActivityIdByStrategyId"
  else
    fail "LocalStrategyActivityMappingPort does not implement queryActivityIdByStrategyId"
  fi

  if grep -q "IRaffleActivityDao" "$LOCAL_MAPPING_PORT" 2>/dev/null; then
    pass "LocalStrategyActivityMappingPort injects IRaffleActivityDao (local delegation)"
  else
    fail "LocalStrategyActivityMappingPort does not inject IRaffleActivityDao"
  fi

  if grep -q "raffleActivityDao.queryStrategyIdByActivityId\|raffleActivityDao\.queryStrategyIdByActivityId" "$LOCAL_MAPPING_PORT" 2>/dev/null; then
    pass "LocalStrategyActivityMappingPort delegates to raffleActivityDao.queryStrategyIdByActivityId"
  else
    fail "LocalStrategyActivityMappingPort does not call raffleActivityDao.queryStrategyIdByActivityId"
  fi

  if grep -q "raffleActivityDao.queryActivityIdByStrategyId\|raffleActivityDao\.queryActivityIdByStrategyId" "$LOCAL_MAPPING_PORT" 2>/dev/null; then
    pass "LocalStrategyActivityMappingPort delegates to raffleActivityDao.queryActivityIdByStrategyId"
  else
    fail "LocalStrategyActivityMappingPort does not call raffleActivityDao.queryActivityIdByStrategyId"
  fi
fi

# ── 5. AL-2/AL-3/AL-4 remain resolved (spot-check) ───────────────────────────
echo ""
echo "── 5. Prior resolutions (AL-2/AL-3/AL-4) still hold ──"

if [[ -f "$STRATEGY_REPO" ]]; then
  if grep -q "IRaffleActivityAccountDao" "$STRATEGY_REPO" 2>/dev/null; then
    fail "StrategyRepository references IRaffleActivityAccountDao — AL-2 regressed"
  else
    pass "StrategyRepository does not reference IRaffleActivityAccountDao (AL-2 still resolved)"
  fi

  if grep -q "IRaffleActivityAccountDayDao" "$STRATEGY_REPO" 2>/dev/null; then
    fail "StrategyRepository references IRaffleActivityAccountDayDao — AL-3 regressed"
  else
    pass "StrategyRepository does not reference IRaffleActivityAccountDayDao (AL-3 still resolved)"
  fi
fi

ACTIVITY_REPO="$INFRA_REPO/ActivityRepository.java"
if [[ -f "$ACTIVITY_REPO" ]]; then
  if grep -q "IUserCreditAccountDao" "$ACTIVITY_REPO" 2>/dev/null; then
    fail "ActivityRepository references IUserCreditAccountDao — AL-4 regressed"
  else
    pass "ActivityRepository does not reference IUserCreditAccountDao (AL-4 still resolved)"
  fi
fi

# ── 6. No new forbidden DAO imports in StrategyRepository ─────────────────────
echo ""
echo "── 6. No new forbidden DAO imports in StrategyRepository ──"

FORBIDDEN_DAOS=(
  "IRaffleActivityDao"
  "IRaffleActivityAccountDao"
  "IRaffleActivityAccountDayDao"
  "IRaffleActivityAccountMonthDao"
  "IUserCreditAccountDao"
  "IUserCreditOrderDao"
  "ICreditAwardTaskDao"
  "IAwardDao"
  "IUserAwardRecordDao"
  "IUserRaffleOrderDao"
  "IDailyBehaviorRebateDao"
  "IUserBehaviorRebateOrderDao"
  "ITaskDao"
)

if [[ -f "$STRATEGY_REPO" ]]; then
  found_any=0
  for dao in "${FORBIDDEN_DAOS[@]}"; do
    if grep -q "$dao" "$STRATEGY_REPO" 2>/dev/null; then
      fail "StrategyRepository has forbidden DAO import: $dao"
      found_any=1
    fi
  done
  if [[ "$found_any" -eq 0 ]]; then
    pass "StrategyRepository has no forbidden DAO imports"
  fi
fi

# ── 7. activity-service scope constraints still hold ─────────────────────────
echo ""
echo "── 7. big-market-activity-service scope constraints ──"

ACT_SRC="$REPO_ROOT/big-market-activity-service/src/main/java"

count_pattern_fail() {
  local label="$1" pattern="$2"
  local cnt
  cnt=$(grep -rn "$pattern" "$ACT_SRC" --include="*.java" 2>/dev/null | wc -l | tr -d ' ')
  if [[ "$cnt" -eq 0 ]]; then
    pass "$label (0 occurrences)"
  else
    fail "$label ($cnt occurrence(s) found)"
  fi
}

count_pattern_fail "No @DubboService in activity-service" "@DubboService"
count_pattern_fail "No @RestController in activity-service" "@RestController"
count_pattern_fail "No @RabbitListener in activity-service" "@RabbitListener"
count_pattern_fail "No @XxlJob in activity-service" "@XxlJob"

ACT_MAPPER_DIR="$REPO_ROOT/big-market-activity-service/src/main/resources/mybatis/mapper"
if [[ -d "$ACT_MAPPER_DIR" ]]; then
  XML_COUNT=$(find "$ACT_MAPPER_DIR" -type f -name "*.xml" 2>/dev/null | wc -l | tr -d ' ')
  if [[ "$XML_COUNT" -eq 0 ]]; then
    pass "activity-service has no mapper XMLs"
  else
    fail "activity-service has $XML_COUNT unexpected mapper XML(s)"
  fi
else
  pass "activity-service mapper directory absent (expected)"
fi

# ── 8. Remote flags remain disabled ───────────────────────────────────────────
echo ""
echo "── 8. Remote / production flag defaults ──"

REMOTE_FLAGS=(
  "account.remote-read.enabled"
  "account.remote-write.enabled"
  "strategy.service.remote-read.enabled"
  "activity.service.remote-draw.enabled"
  "activity.service.remote-strategy-mapping.enabled"
)

RESOURCE_DIRS=(
  "$REPO_ROOT/big-market-account-service/src/main/resources"
  "$REPO_ROOT/big-market-market-service/src/main/resources"
  "$REPO_ROOT/big-market-strategy-service/src/main/resources"
  "$REPO_ROOT/big-market-activity-service/src/main/resources"
)

for flag in "${REMOTE_FLAGS[@]}"; do
  found=0
  for dir in "${RESOURCE_DIRS[@]}"; do
    cnt=$(grep -rn "${flag}.*:.*true\|${flag}=true" "$dir" \
      --include="*.yml" --include="*.yaml" --include="*.properties" \
      2>/dev/null | grep -cv "^[[:space:]]*#" || true)
    found=$((found + cnt))
  done
  dc_cnt=$(grep -n "${flag}.*true" "$REPO_ROOT/docker-compose.yml" 2>/dev/null \
    | grep -cv "^[[:space:]]*#" || true)
  found=$((found + dc_cnt))
  if [[ "$found" -eq 0 ]]; then
    pass "Flag default safe: $flag"
  else
    fail "Flag appears enabled: $flag ($found match(es))"
  fi
done

# ── 9. Phase 6-B validator passes ────────────────────────────────────────────
echo ""
echo "── 9. Phase 6-B package-ownership validator ──"

PHASE6B_SCRIPT="$REPO_ROOT/scripts/validate-microservices-phase-6-package-ownership-boundaries.sh"
if [[ ! -f "$PHASE6B_SCRIPT" ]]; then
  fail "Phase 6-B validator not found: $PHASE6B_SCRIPT"
else
  if bash "$PHASE6B_SCRIPT" > /tmp/phase6b_al1_output.txt 2>&1; then
    pass "Phase 6-B validator passed"
  else
    fail "Phase 6-B validator FAILED — see output below"
    cat /tmp/phase6b_al1_output.txt
  fi
fi

# ── Summary ──────────────────────────────────────────────────────────────────
echo ""
echo "========================================================================"
echo "  SUMMARY"
echo "========================================================================"
echo "Checks passed: $PASS"
echo "Checks failed: $FAIL"
echo ""

if [[ "$FAIL" -eq 0 ]]; then
  echo "RESULT: ALL CHECKS PASSED — Phase 7-A AL-1 strategy-activity mapping boundary complete"
  echo "        AL-1 (StrategyRepository -> IRaffleActivityDao) is RESOLVED."
  echo "        StrategyRepository now routes ID-mapping reads through IStrategyActivityMappingPort."
  echo "        LocalStrategyActivityMappingPort delegates to IRaffleActivityDao."
  echo "        AL-2, AL-3, AL-4 remain resolved."
  echo "        Next recommended batch: Phase 7-B (generic task table ownership decision)."
  exit 0
else
  echo "RESULT: $FAIL CHECK(S) FAILED — review output above"
  exit 1
fi

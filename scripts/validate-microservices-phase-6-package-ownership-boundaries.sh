#!/usr/bin/env bash
# validate-microservices-phase-6-package-ownership-boundaries.sh
#
# Phase 6-B validator: package and DAO boundary assertions.
#
# Converts the Phase 6-A DAO ownership matrix into repeatable CI-style checks.
# All known cross-boundary violations are explicitly allowlisted; any new
# coupling not on that list causes a failure.
#
# Hard constraints honoured:
#   - No Java files changed (scripts and docs only)
#   - No DDL executed
#   - No remote flags enabled
#   - No external services required
#
# ──────────────────────────────────────────────────────────────────────────────
# ALLOWLIST — current known cross-boundary violations
# (must be removed before Phase 7-A table isolation proceeds)
#
#   [AL-1] StrategyRepository -> IRaffleActivityDao  *** RESOLVED — Phase 7-A (AL-1) ***
#          context: strategy reads activity table for activityId <-> strategyId mapping
#          resolution: routed through IStrategyActivityMappingPort.queryActivityIdByStrategyId /
#                      queryStrategyIdByActivityId; LocalStrategyActivityMappingPort delegates to
#                      IRaffleActivityDao; StrategyRepository no longer imports IRaffleActivityDao directly.
#   [AL-2] StrategyRepository -> IRaffleActivityAccountDao  *** RESOLVED — Phase 7-A prep (AL-2/AL-3) ***
#          context: strategy reads quota for totalRaffleCount rule
#          resolution: routed through IStrategyActivityAccountPort.queryTotalUseCount;
#                      LocalStrategyActivityAccountPort delegates to IRaffleActivityAccountDao;
#                      StrategyRepository no longer imports IRaffleActivityAccountDao directly.
#   [AL-3] StrategyRepository -> IRaffleActivityAccountDayDao  *** RESOLVED — Phase 7-A prep (AL-2/AL-3) ***
#          context: strategy reads day-quota for dayRaffleCount rule
#          resolution: routed through IStrategyActivityAccountPort.queryTodayRaffleCount;
#                      LocalStrategyActivityAccountPort delegates to IRaffleActivityAccountDayDao;
#                      StrategyRepository no longer imports IRaffleActivityAccountDayDao directly.
#   [AL-4] ActivityRepository -> IUserCreditAccountDao  *** RESOLVED — Phase 7-A prep ***
#          context: activity reads credit balance for SKU credit-purchase partake
#          resolution: routed through IActivityAccountPort.queryUserCreditAccountAmount;
#                      LocalActivityAccountPort delegates to IUserCreditAccountDao;
#                      ActivityRepository no longer imports IUserCreditAccountDao directly.
#   [AL-5] AwardRepository -> IUserRaffleOrderDao
#          context: fulfillment reads raffle order status before writing award record
#   [AL-6] AwardRepository -> IUserCreditAccountDao
#          context: fulfillment local-tx credit write (flag-gated outbox path)
#   [AL-7] DispatchCreditAwardTaskJob -> ICreditAwardTaskDao
#          context: message-job-service reads credit_award_task directly (flag false)
#   [AL-8] BehaviorRebateRepository -> ITaskDao  (shared task outbox)
#          context: rebate writes to shared task outbox table
#   [AL-9]  CreditRepository -> ITaskDao  (shared task outbox)
#           context: credit writes to shared task outbox table
#   [AL-10] AwardRepository -> ITaskDao  (shared task outbox)
#           context: fulfillment writes to shared task outbox (e.g. send_award outbox)
#   [AL-11] AwardRepository -> ICreditAwardTaskDao
#           context: fulfillment writes credit_award_task outbox row in saveGiveOutPrizesAggregate
#
# ──────────────────────────────────────────────────────────────────────────────

set -uo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PASS=0
FAIL=0

pass() { echo "[PASS] $*"; PASS=$((PASS + 1)); }
fail() { echo "[FAIL] $*"; FAIL=$((FAIL + 1)); }

INFRA_DAO_PKG="com.dyx.market.infrastructure.dao"
INFRA_REPO="$REPO_ROOT/big-market-infrastructure/src/main/java/com/dyx/market/infrastructure/adapter/repository"
INFRA_JAVA="$REPO_ROOT/big-market-infrastructure/src/main/java"

echo ""
echo "========================================================================"
echo "  Phase 6-B: Package Ownership Boundary Validator"
echo "  Repo: $REPO_ROOT"
echo "========================================================================"

# ── 1. Phase 6-A ownership matrix exists and names key violations ─────────────
echo ""
echo "── 1. Phase 6-A matrix doc integrity ──"

DOC="$REPO_ROOT/docs/microservices-dao-ownership.md"
if [[ -f "$DOC" ]]; then
  pass "docs/microservices-dao-ownership.md exists"
else
  fail "docs/microservices-dao-ownership.md missing — Phase 6-A artifact required"
fi

# Confirm the doc still names each allowlisted violation explicitly
check_violation_in_doc() {
  local label="$1" classA="$2" classB="$3"
  if grep -q "$classA" "$DOC" 2>/dev/null && grep -q "$classB" "$DOC" 2>/dev/null; then
    pass "Matrix names violation: $label"
  else
    fail "Matrix missing violation: $label — doc must name both $classA and $classB"
  fi
}

check_violation_in_doc "AL-1 StrategyRepository->IRaffleActivityDao"        "StrategyRepository"        "IRaffleActivityDao"
check_violation_in_doc "AL-2 StrategyRepository->IRaffleActivityAccountDao" "StrategyRepository"        "IRaffleActivityAccountDao"
check_violation_in_doc "AL-3 StrategyRepository->IRaffleActivityAccountDayDao" "StrategyRepository"     "IRaffleActivityAccountDayDao"
check_violation_in_doc "AL-4 ActivityRepository->IUserCreditAccountDao (resolved Phase 7-A prep)" "ActivityRepository" "IUserCreditAccountDao"
check_violation_in_doc "AL-5 AwardRepository->IUserRaffleOrderDao"          "AwardRepository"           "IUserRaffleOrderDao"
check_violation_in_doc "AL-6 AwardRepository->IUserCreditAccountDao"        "AwardRepository"           "IUserCreditAccountDao"
check_violation_in_doc "AL-7 DispatchCreditAwardTaskJob->ICreditAwardTaskDao" "DispatchCreditAwardTaskJob" "ICreditAwardTaskDao"
check_violation_in_doc "AL-8 BehaviorRebateRepository->ITaskDao"            "BehaviorRebateRepository"  "ITaskDao"
check_violation_in_doc "AL-9 CreditRepository->ITaskDao"                    "CreditRepository"          "ITaskDao"
check_violation_in_doc "AL-10 AwardRepository->ITaskDao"                    "AwardRepository"           "ITaskDao"
check_violation_in_doc "AL-11 AwardRepository->ICreditAwardTaskDao"         "AwardRepository"           "ICreditAwardTaskDao"

# ── 2. Allowlisted violations still present (not silently removed) ────────────
echo ""
echo "── 2. Allowlisted cross-boundary usage verified in source ──"

check_field_present() {
  local label="$1" file="$2" pattern="$3"
  if [[ ! -f "$file" ]]; then
    fail "$label — file not found: $file"
    return
  fi
  if grep -q "$pattern" "$file" 2>/dev/null; then
    pass "$label — [ALLOWLISTED] found as expected"
  else
    # The violation may have been removed — that's good progress but the
    # allowlist entry must then be updated.  Warn rather than hard-fail so
    # removal is visible but doesn't break the build.
    echo "[WARN] $label — allowlisted violation NOT found in source."
    echo "       If the cross-boundary access was intentionally removed,"
    echo "       update the allowlist in this script accordingly."
  fi
}

# AL-1 StrategyRepository->IRaffleActivityDao — RESOLVED in Phase 7-A (AL-1).
# StrategyRepository now routes activityId <-> strategyId reads through
# IStrategyActivityMappingPort (LocalStrategyActivityMappingPort delegates to
# IRaffleActivityDao). The forbidden-DAO check below enforces that the direct
# coupling does not regress.

# AL-2 StrategyRepository->IRaffleActivityAccountDao — RESOLVED in Phase 7-A prep (AL-2/AL-3).
# StrategyRepository now routes total-use-count reads through
# IStrategyActivityAccountPort.queryTotalUseCount (LocalStrategyActivityAccountPort
# delegates to IRaffleActivityAccountDao). The forbidden-DAO check below enforces
# that the direct coupling does not regress.

# AL-3 StrategyRepository->IRaffleActivityAccountDayDao — RESOLVED in Phase 7-A prep (AL-2/AL-3).
# StrategyRepository now routes today-raffle-count reads through
# IStrategyActivityAccountPort.queryTodayRaffleCount (LocalStrategyActivityAccountPort
# delegates to IRaffleActivityAccountDayDao). The forbidden-DAO check below enforces
# that the direct coupling does not regress.

# AL-4 ActivityRepository->IUserCreditAccountDao — RESOLVED in Phase 7-A prep.
# The violation has been removed; ActivityRepository now routes credit-account reads
# through IActivityAccountPort.queryUserCreditAccountAmount (LocalActivityAccountPort
# delegates to IUserCreditAccountDao). The forbidden-DAO check below enforces
# that the direct coupling does not regress.

check_field_present \
  "AL-5 AwardRepository->IUserRaffleOrderDao" \
  "$INFRA_REPO/AwardRepository.java" \
  "IUserRaffleOrderDao"

check_field_present \
  "AL-6 AwardRepository->IUserCreditAccountDao" \
  "$INFRA_REPO/AwardRepository.java" \
  "IUserCreditAccountDao"

DISPATCH_JOB=$(find "$REPO_ROOT/big-market-message-job-service/src" \
  -name "DispatchCreditAwardTaskJob.java" ! -path "*/target/*" 2>/dev/null | head -1)
check_field_present \
  "AL-7 DispatchCreditAwardTaskJob->ICreditAwardTaskDao" \
  "${DISPATCH_JOB:-/dev/null}" \
  "ICreditAwardTaskDao"

check_field_present \
  "AL-8 BehaviorRebateRepository->ITaskDao" \
  "$INFRA_REPO/BehaviorRebateRepository.java" \
  "ITaskDao"

check_field_present \
  "AL-9 CreditRepository->ITaskDao" \
  "$INFRA_REPO/CreditRepository.java" \
  "ITaskDao"

check_field_present \
  "AL-10 AwardRepository->ITaskDao" \
  "$INFRA_REPO/AwardRepository.java" \
  "ITaskDao"

check_field_present \
  "AL-11 AwardRepository->ICreditAwardTaskDao" \
  "$INFRA_REPO/AwardRepository.java" \
  "ICreditAwardTaskDao"

# ── 3. No NEW cross-boundary DAO usage outside the allowlist ──────────────────
echo ""
echo "── 3. No new cross-boundary DAO coupling outside allowlist ──"

# For each repository, assert it does NOT import a DAO from a foreign context
# beyond those in the allowlist.
#
# Ownership map (simplified):
#   StrategyRepository    -> owns: IStrategyDao, IStrategyAwardDao, IStrategyRuleDao,
#                                   IRuleTreeDao, IRuleTreeNodeDao, IRuleTreeNodeLineDao
#                            allowed foreign: IRaffleActivityDao (AL-1)
#                            resolved: IRaffleActivityAccountDao (AL-2), IRaffleActivityAccountDayDao (AL-3)
#
#   ActivityRepository    -> owns: IRaffleActivityDao, IRaffleActivityCountDao,
#                                   IRaffleActivitySkuDao, IRaffleActivityStageDao,
#                                   IRaffleActivityOrderDao, IUserRaffleOrderDao,
#                                   IRaffleActivityAccountDao, IRaffleActivityAccountDayDao,
#                                   IRaffleActivityAccountMonthDao, IRaffleQuotaDecrementLedgerDao
#                            resolved: IUserCreditAccountDao (AL-4)
#
#   AwardRepository       -> owns: IAwardDao, IUserAwardRecordDao
#                            allowed foreign: IUserRaffleOrderDao (AL-5), IUserCreditAccountDao (AL-6)
#
#   BehaviorRebateRepository -> owns: IDailyBehaviorRebateDao, IUserBehaviorRebateOrderDao
#                            allowed foreign: ITaskDao (AL-8)
#
#   CreditRepository      -> owns: IUserCreditAccountDao, IUserCreditOrderDao, ICreditAwardTaskDao
#                            allowed foreign: ITaskDao (AL-9)
#
#   TaskRepository        -> owns: ITaskDao
#
#   ESUserRaffleOrderRepository -> owns: IElasticSearchUserRaffleOrderDao

check_no_forbidden_dao() {
  local label="$1" repo_file="$2"
  shift 2
  local forbidden=("$@")
  if [[ ! -f "$repo_file" ]]; then
    fail "$label — file not found: $repo_file"
    return
  fi
  local found_any=0
  for dao in "${forbidden[@]}"; do
    if grep -q "$dao" "$repo_file" 2>/dev/null; then
      fail "$label — NEW forbidden cross-boundary DAO import: $dao"
      found_any=1
    fi
  done
  if [[ "$found_any" -eq 0 ]]; then
    pass "$label — no new forbidden DAO imports"
  fi
}

# StrategyRepository must not import any DAO outside its own context.
# AL-1 (IRaffleActivityDao) was resolved in Phase 7-A — now explicitly forbidden.
# AL-2 (IRaffleActivityAccountDao) and AL-3 (IRaffleActivityAccountDayDao) were
# resolved in Phase 7-A prep — already forbidden.
check_no_forbidden_dao \
  "StrategyRepository forbidden DAOs" \
  "$INFRA_REPO/StrategyRepository.java" \
  "IRaffleActivityDao" \
  "IRaffleActivityAccountDao" \
  "IRaffleActivityAccountDayDao" \
  "IRaffleActivityAccountMonthDao" \
  "IUserCreditAccountDao" \
  "IUserCreditOrderDao" \
  "ICreditAwardTaskDao" \
  "IAwardDao" \
  "IUserAwardRecordDao" \
  "IUserRaffleOrderDao" \
  "IDailyBehaviorRebateDao" \
  "IUserBehaviorRebateOrderDao" \
  "ITaskDao"

# ActivityRepository must not import DAOs outside its context.
# AL-4 (IUserCreditAccountDao) was resolved in Phase 7-A prep — now explicitly forbidden.
check_no_forbidden_dao \
  "ActivityRepository forbidden DAOs" \
  "$INFRA_REPO/ActivityRepository.java" \
  "IStrategyDao" \
  "IStrategyAwardDao" \
  "IStrategyRuleDao" \
  "IRuleTreeDao" \
  "IRuleTreeNodeDao" \
  "IRuleTreeNodeLineDao" \
  "IAwardDao" \
  "IUserAwardRecordDao" \
  "IUserCreditAccountDao" \
  "IUserCreditOrderDao" \
  "ICreditAwardTaskDao" \
  "IDailyBehaviorRebateDao" \
  "IUserBehaviorRebateOrderDao" \
  "ITaskDao"

# AwardRepository must not import DAOs outside its context or AL-5,6,10,11.
# Allowed foreign: IUserRaffleOrderDao (AL-5), IUserCreditAccountDao (AL-6),
#                  ITaskDao (AL-10), ICreditAwardTaskDao (AL-11)
check_no_forbidden_dao \
  "AwardRepository forbidden DAOs" \
  "$INFRA_REPO/AwardRepository.java" \
  "IStrategyDao" \
  "IStrategyAwardDao" \
  "IStrategyRuleDao" \
  "IRuleTreeDao" \
  "IRuleTreeNodeDao" \
  "IRuleTreeNodeLineDao" \
  "IRaffleActivityDao" \
  "IRaffleActivityCountDao" \
  "IRaffleActivitySkuDao" \
  "IRaffleActivityStageDao" \
  "IRaffleActivityOrderDao" \
  "IRaffleActivityAccountDao" \
  "IRaffleActivityAccountDayDao" \
  "IRaffleActivityAccountMonthDao" \
  "IRaffleQuotaDecrementLedgerDao" \
  "IUserCreditOrderDao" \
  "IDailyBehaviorRebateDao" \
  "IUserBehaviorRebateOrderDao"

# BehaviorRebateRepository must not import DAOs outside its context or AL-8.
check_no_forbidden_dao \
  "BehaviorRebateRepository forbidden DAOs" \
  "$INFRA_REPO/BehaviorRebateRepository.java" \
  "IStrategyDao" \
  "IStrategyAwardDao" \
  "IStrategyRuleDao" \
  "IRuleTreeDao" \
  "IRaffleActivityDao" \
  "IRaffleActivityCountDao" \
  "IRaffleActivitySkuDao" \
  "IRaffleActivityStageDao" \
  "IRaffleActivityOrderDao" \
  "IRaffleActivityAccountDao" \
  "IRaffleActivityAccountDayDao" \
  "IRaffleActivityAccountMonthDao" \
  "IRaffleQuotaDecrementLedgerDao" \
  "IUserRaffleOrderDao" \
  "IUserCreditAccountDao" \
  "IUserCreditOrderDao" \
  "ICreditAwardTaskDao" \
  "IAwardDao" \
  "IUserAwardRecordDao"

# CreditRepository must not import DAOs outside its context or AL-9.
check_no_forbidden_dao \
  "CreditRepository forbidden DAOs" \
  "$INFRA_REPO/CreditRepository.java" \
  "IStrategyDao" \
  "IStrategyAwardDao" \
  "IStrategyRuleDao" \
  "IRuleTreeDao" \
  "IRaffleActivityDao" \
  "IRaffleActivityCountDao" \
  "IRaffleActivitySkuDao" \
  "IRaffleActivityStageDao" \
  "IRaffleActivityOrderDao" \
  "IRaffleActivityAccountDao" \
  "IRaffleActivityAccountDayDao" \
  "IRaffleActivityAccountMonthDao" \
  "IRaffleQuotaDecrementLedgerDao" \
  "IUserRaffleOrderDao" \
  "IAwardDao" \
  "IUserAwardRecordDao" \
  "IDailyBehaviorRebateDao" \
  "IUserBehaviorRebateOrderDao"

# ── 4. No new mapper XML ownership movement ───────────────────────────────────
echo ""
echo "── 4. No new mapper XML ownership movement ──"
# Canonical: mapper XMLs known to each service module.
# activity-service must still have zero mapper XMLs.
ACT_MAPPERS=$(find "$REPO_ROOT/big-market-activity-service/src/main/resources/mybatis/mapper" \
  -type f -name "*.xml" 2>/dev/null | wc -l | tr -d ' ')
if [[ "$ACT_MAPPERS" -eq 0 ]]; then
  pass "big-market-activity-service has no mapper XMLs (correct)"
else
  fail "big-market-activity-service has $ACT_MAPPERS mapper XML(s) — unexpected; no mapper migration in this batch"
fi

# Verify well-known service mapper directories still contain their expected files
check_mapper_present() {
  local label="$1" rel_path="$2"
  if [[ -f "$REPO_ROOT/$rel_path" ]]; then
    pass "$label"
  else
    fail "$label — expected file missing: $rel_path"
  fi
}

check_mapper_present "account-service has raffle_activity_account mapper" \
  "big-market-account-service/src/main/resources/mybatis/mapper/mysql/raffle_activity_account_mapper.xml"
check_mapper_present "market-service has raffle_activity mapper" \
  "big-market-market-service/src/main/resources/mybatis/mapper/mysql/raffle_activity_mapper.xml"
check_mapper_present "strategy-service has strategy mapper" \
  "big-market-strategy-service/src/main/resources/mybatis/mapper/mysql/strategy_mapper.xml"
check_mapper_present "rebate-service has daily_behavior_rebate mapper" \
  "big-market-rebate-service/src/main/resources/mybatis/mapper/mysql/daily_behavior_rebate_mapper.xml"

# ── 5. activity-service scope constraints still hold ─────────────────────────
echo ""
echo "── 5. big-market-activity-service scope constraints ──"

ACT_SRC="$REPO_ROOT/big-market-activity-service/src/main/java"

count_pattern() {
  local label="$1" pattern="$2"
  local cnt
  cnt=$(grep -rn "$pattern" "$ACT_SRC" --include="*.java" 2>/dev/null | wc -l | tr -d ' ')
  if [[ "$cnt" -eq 0 ]]; then
    pass "$label (0 occurrences)"
  else
    fail "$label ($cnt occurrence(s) found)"
  fi
}

count_pattern "No mapper XMLs in activity-service" "@Mapper"
count_pattern "No @DubboService in activity-service" "@DubboService"
count_pattern "No @RestController in activity-service" "@RestController"
count_pattern "No @RabbitListener in activity-service" "@RabbitListener"
count_pattern "No @XxlJob in activity-service" "@XxlJob"

# Also verify the mapper XML check (belt-and-suspenders with §4)
ACT_MAPPER_DIR="$REPO_ROOT/big-market-activity-service/src/main/resources/mybatis/mapper"
if [[ -d "$ACT_MAPPER_DIR" ]]; then
  XML_COUNT=$(find "$ACT_MAPPER_DIR" -type f -name "*.xml" 2>/dev/null | wc -l | tr -d ' ')
  if [[ "$XML_COUNT" -eq 0 ]]; then
    pass "No mapper XML files in activity-service mapper directory"
  else
    fail "$XML_COUNT mapper XML file(s) found in activity-service"
  fi
else
  pass "activity-service mapper directory absent (expected)"
fi

# ── 6. Phase 5-D/E/F/G port boundaries still hold ────────────────────────────
echo ""
echo "── 6. Phase 5-D/E/F/G port boundaries ──"

check_file_exists() {
  local label="$1" path="$2"
  if [[ -f "$path" ]]; then
    pass "$label"
  else
    fail "$label — missing: $path"
  fi
}

check_file_exists \
  "Phase 5-D IStrategyDecisionPort exists" \
  "$(find "$REPO_ROOT" -path "*/domain/activity/adapter/port/IStrategyDecisionPort.java" ! -path "*/target/*" 2>/dev/null | head -1)"

check_file_exists \
  "Phase 5-D LocalStrategyDecisionPort exists" \
  "$(find "$REPO_ROOT" -name "LocalStrategyDecisionPort.java" ! -path "*/target/*" 2>/dev/null | head -1)"

check_file_exists \
  "Phase 5-E IAwardFulfillmentPort exists" \
  "$(find "$REPO_ROOT" -path "*/domain/activity/adapter/port/IAwardFulfillmentPort.java" ! -path "*/target/*" 2>/dev/null | head -1)"

check_file_exists \
  "Phase 5-E LocalAwardFulfillmentPort exists" \
  "$(find "$REPO_ROOT" -name "LocalAwardFulfillmentPort.java" ! -path "*/target/*" 2>/dev/null | head -1)"

check_file_exists \
  "Phase 5-F ActivityServiceApplication exists" \
  "$REPO_ROOT/big-market-activity-service/src/main/java/com/dyx/market/activity/ActivityServiceApplication.java"

check_file_exists \
  "Phase 5-G IDrawOutboxPort exists" \
  "$(find "$REPO_ROOT" -path "*/domain/activity/adapter/port/IDrawOutboxPort.java" ! -path "*/target/*" 2>/dev/null | head -1)"

check_file_exists \
  "Phase 5-G LocalDrawOutboxPort exists" \
  "$(find "$REPO_ROOT" -name "LocalDrawOutboxPort.java" ! -path "*/target/*" 2>/dev/null | head -1)"

# ── 7. No production or remote traffic flags enabled ─────────────────────────
echo ""
echo "── 7. Remote / production flag defaults ──"

REMOTE_FLAGS=(
  "account.remote-read.enabled"
  "account.remote-write.enabled"
  "account.award-credit-outbox.enabled"
  "rebate.remote-create-order.enabled"
  "rebate.service.remote-read.enabled"
  "strategy.service.remote-read.enabled"
  "fulfillment.remote.enabled"
  "activity.service.remote-draw.enabled"
  "award.service.remote-fulfillment.enabled"
  "strategy.service.remote-decision.enabled"
)

RESOURCE_DIRS=(
  "$REPO_ROOT/big-market-account-service/src/main/resources"
  "$REPO_ROOT/big-market-market-service/src/main/resources"
  "$REPO_ROOT/big-market-message-job-service/src/main/resources"
  "$REPO_ROOT/big-market-rebate-service/src/main/resources"
  "$REPO_ROOT/big-market-strategy-service/src/main/resources"
  "$REPO_ROOT/big-market-activity-service/src/main/resources"
  "$REPO_ROOT/big-market-fulfillment-service/src/main/resources"
)

for flag in "${REMOTE_FLAGS[@]}"; do
  found=0
  for dir in "${RESOURCE_DIRS[@]}"; do
    cnt=$(grep -rn "${flag}.*:.*true\|${flag}=true" "$dir" \
      --include="*.yml" --include="*.yaml" --include="*.properties" \
      2>/dev/null | grep -cv "^[[:space:]]*#" || true)
    found=$((found + cnt))
  done
  # Also check docker-compose.yml
  dc_cnt=$(grep -n "${flag}.*true" "$REPO_ROOT/docker-compose.yml" 2>/dev/null \
    | grep -cv "^[[:space:]]*#" || true)
  found=$((found + dc_cnt))

  if [[ "$found" -eq 0 ]]; then
    pass "Flag default safe: $flag"
  else
    fail "Flag appears enabled: $flag ($found match(es))"
  fi
done

# ── 8. Phase 6-B noted in master plan ────────────────────────────────────────
echo ""
echo "── 8. Master plan updated for Phase 6-B ──"
MASTER="$REPO_ROOT/docs/microservices-decomposition-master-plan.md"
if grep -q "6-B.*Done\|6-B.*done\|Phase 6-B.*complete\|6-B.*package.*Done\|6-B.*package.*done" "$MASTER" 2>/dev/null; then
  pass "Master plan marks Phase 6-B done"
else
  fail "Master plan does not mark Phase 6-B done"
fi

# ── 9. Phase 6-B enforcement note in DAO ownership doc ───────────────────────
echo ""
echo "── 9. DAO ownership doc references Phase 6-B enforcement ──"
if grep -q "Phase 6-B\|6-B\|phase-6-package" "$DOC" 2>/dev/null; then
  pass "docs/microservices-dao-ownership.md references Phase 6-B"
else
  fail "docs/microservices-dao-ownership.md does not reference Phase 6-B enforcement"
fi

# ── 10. (retired) Docs-and-scripts-only batch constraint ─────────────────────
# This check was a one-time constraint for the Phase 6-B commit. Phase 7+
# batches legitimately change Java files, so the check is retired here.
# Java boundary safety is enforced by the forbidden-DAO checks in §3 above.

# ── Summary ──────────────────────────────────────────────────────────────────
echo ""
echo "========================================================================"
echo "  SUMMARY"
echo "========================================================================"
echo "Checks passed: $PASS"
echo "Checks failed: $FAIL"
echo ""
echo "Allowlisted violations (not new failures — must be fixed before Phase 7-A):"
echo "  AL-1  StrategyRepository -> IRaffleActivityDao  [RESOLVED Phase 7-A AL-1]"
echo "  AL-2  StrategyRepository -> IRaffleActivityAccountDao  [RESOLVED Phase 7-A prep AL-2/AL-3]"
echo "  AL-3  StrategyRepository -> IRaffleActivityAccountDayDao  [RESOLVED Phase 7-A prep AL-2/AL-3]"
echo "  AL-4  ActivityRepository -> IUserCreditAccountDao  [RESOLVED Phase 7-A prep]"
echo "  AL-5  AwardRepository    -> IUserRaffleOrderDao"
echo "  AL-6  AwardRepository    -> IUserCreditAccountDao"
echo "  AL-7  DispatchCreditAwardTaskJob -> ICreditAwardTaskDao  (flag false)"
echo "  AL-8  BehaviorRebateRepository  -> ITaskDao  (shared outbox)"
echo "  AL-9  CreditRepository          -> ITaskDao  (shared outbox)"
echo "  AL-10 AwardRepository           -> ITaskDao  (shared outbox)"
echo "  AL-11 AwardRepository           -> ICreditAwardTaskDao  (credit outbox write)"
echo ""

if [[ "$FAIL" -eq 0 ]]; then
  echo "RESULT: ALL CHECKS PASSED — Phase 6-B package ownership boundary validator complete"
  exit 0
else
  echo "RESULT: $FAIL CHECK(S) FAILED — review output above"
  exit 1
fi

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
#   [AL-5] AwardRepository -> IUserRaffleOrderDao  *** RESOLVED — Phase 7-A prep (AL-5) ***
#          context: fulfillment reads raffle order status before writing award record
#          resolution: routed through IAwardActivityOrderPort.markUserRaffleOrderUsed;
#                      LocalAwardActivityOrderPort delegates to IUserRaffleOrderDao;
#                      AwardRepository no longer imports IUserRaffleOrderDao directly.
#   [AL-6] AwardRepository -> IUserCreditAccountDao  *** RESOLVED — Phase 7-A prep (AL-6/AL-11) ***
#          context: fulfillment local-tx credit write (flag-gated outbox path)
#          resolution: routed through IAwardCreditWritePort.updateOrCreateCreditAccount;
#                      LocalAwardCreditWritePort delegates to IUserCreditAccountDao;
#                      AwardRepository no longer imports IUserCreditAccountDao directly.
#   [AL-7] DispatchCreditAwardTaskJob -> ICreditAwardTaskDao  *** RESOLVED — Phase 7-A prep (AL-7) ***
#          context: message-job-service reads credit_award_task directly (flag false)
#          resolution: routed through ICreditAwardTaskDispatchPort.queryPendingTasks /
#                      updateDispatched / updateRetryFailed; LocalCreditAwardTaskDispatchPort
#                      delegates to ICreditAwardTaskDao; DispatchCreditAwardTaskJob no longer
#                      imports ICreditAwardTaskDao directly.
#   [AL-8] BehaviorRebateRepository -> ITaskDao  *** RESOLVED — Phase 7-C ***
#          context: rebate writes to shared task outbox table
#          resolution: routed through IRebateTaskOutboxPort; LocalRebateTaskOutboxPort
#                      delegates to ITaskDao until Phase 8 physical table cutover.
#   [AL-9]  CreditRepository -> ITaskDao  *** RESOLVED — Phase 7-C ***
#           context: credit writes to shared task outbox table
#           resolution: routed through ICreditTradeTaskOutboxPort; LocalCreditTradeTaskOutboxPort
#                       delegates to ITaskDao until Phase 8 physical table cutover.
#   [AL-10] AwardRepository -> ITaskDao  *** RESOLVED — Phase 7-C ***
#           context: fulfillment writes to shared task outbox (e.g. send_award outbox)
#           resolution: routed through IAwardDispatchTaskOutboxPort; LocalAwardDispatchTaskOutboxPort
#                       delegates to ITaskDao until Phase 8 physical table cutover.
#   [AL-11] AwardRepository -> ICreditAwardTaskDao  *** RESOLVED — Phase 7-A prep (AL-6/AL-11) ***
#           context: fulfillment writes credit_award_task outbox row in saveGiveOutPrizesAggregate
#           resolution: routed through IAwardCreditWritePort.insertCreditAwardTask;
#                       LocalAwardCreditWritePort delegates to ICreditAwardTaskDao;
#                       AwardRepository no longer imports ICreditAwardTaskDao directly.
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
check_violation_in_doc "AL-5 AwardRepository->IUserRaffleOrderDao (resolved Phase 7-A prep)" "AwardRepository" "IUserRaffleOrderDao"
check_violation_in_doc "AL-6 AwardRepository->IUserCreditAccountDao (resolved Phase 7-A prep)" "AwardRepository" "IUserCreditAccountDao"
check_violation_in_doc "AL-7 DispatchCreditAwardTaskJob->ICreditAwardTaskDao (resolved Phase 7-A prep)" "DispatchCreditAwardTaskJob" "ICreditAwardTaskDao"
check_violation_in_doc "AL-8 BehaviorRebateRepository->ITaskDao"            "BehaviorRebateRepository"  "ITaskDao"
check_violation_in_doc "AL-9 CreditRepository->ITaskDao"                    "CreditRepository"          "ITaskDao"
check_violation_in_doc "AL-10 AwardRepository->ITaskDao"                    "AwardRepository"           "ITaskDao"
check_violation_in_doc "AL-11 AwardRepository->ICreditAwardTaskDao (resolved Phase 7-A prep)" "AwardRepository" "ICreditAwardTaskDao"

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

# AL-5 AwardRepository->IUserRaffleOrderDao — RESOLVED in Phase 7-A prep (AL-5).
# AwardRepository now routes the guarded user_raffle_order create->used transition
# through IAwardActivityOrderPort.markUserRaffleOrderUsed (LocalAwardActivityOrderPort
# delegates to IUserRaffleOrderDao). The forbidden-DAO check below enforces that
# the direct coupling does not regress.

# AL-6 AwardRepository->IUserCreditAccountDao — RESOLVED in Phase 7-A prep (AL-6/AL-11).
# AwardRepository now routes direct credit-account writes through
# IAwardCreditWritePort.updateOrCreateCreditAccount (LocalAwardCreditWritePort
# delegates to IUserCreditAccountDao). The forbidden-DAO check below enforces
# that the direct coupling does not regress.

DISPATCH_JOB=$(find "$REPO_ROOT/big-market-message-job-service/src" \
  -name "DispatchCreditAwardTaskJob.java" ! -path "*/target/*" 2>/dev/null | head -1)
# AL-7 DispatchCreditAwardTaskJob->ICreditAwardTaskDao — RESOLVED in Phase 7-A prep (AL-7).
# DispatchCreditAwardTaskJob now routes credit_award_task reads and state transitions
# through ICreditAwardTaskDispatchPort (LocalCreditAwardTaskDispatchPort delegates to
# ICreditAwardTaskDao). The job-level forbidden-DAO check below enforces that the
# direct coupling does not regress.

# AL-8/AL-9/AL-10 repository->ITaskDao — RESOLVED in Phase 7-C.
# The repositories now route task outbox writes through domain ports. Local
# infrastructure adapters deliberately preserve ITaskDao delegation until DBA
# DDL and Phase 8 cutover gates are complete.

# AL-11 AwardRepository->ICreditAwardTaskDao — RESOLVED in Phase 7-A prep (AL-6/AL-11).
# AwardRepository now routes credit_award_task inserts through
# IAwardCreditWritePort.insertCreditAwardTask (LocalAwardCreditWritePort
# delegates to ICreditAwardTaskDao). The forbidden-DAO check below enforces
# that the direct coupling does not regress.

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
#                            resolved: IUserRaffleOrderDao (AL-5)
#                            resolved: IUserCreditAccountDao (AL-6),
#                                      ICreditAwardTaskDao (AL-11)
#
#   BehaviorRebateRepository -> owns: IDailyBehaviorRebateDao, IUserBehaviorRebateOrderDao
#                            allowed foreign: ITaskDao (AL-8; Phase 7-B decision complete, runtime unresolved)
#
#   CreditRepository      -> owns: IUserCreditAccountDao, IUserCreditOrderDao, ICreditAwardTaskDao
#                            allowed foreign: ITaskDao (AL-9; Phase 7-B decision complete, runtime unresolved)
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

# AwardRepository must not import DAOs outside its context.
# AL-5 (IUserRaffleOrderDao) was resolved in Phase 7-A prep — now explicitly forbidden.
# AL-6 (IUserCreditAccountDao) and AL-11 (ICreditAwardTaskDao) were resolved in
# Phase 7-A prep — now explicitly forbidden.
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
  "IUserRaffleOrderDao" \
  "IRaffleActivityAccountDao" \
  "IRaffleActivityAccountDayDao" \
  "IRaffleActivityAccountMonthDao" \
  "IRaffleQuotaDecrementLedgerDao" \
  "IUserCreditAccountDao" \
  "IUserCreditOrderDao" \
  "ICreditAwardTaskDao" \
  "IDailyBehaviorRebateDao" \
  "IUserBehaviorRebateOrderDao" \
  "ITaskDao"

# BehaviorRebateRepository must not import DAOs outside its context.
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
  "IUserAwardRecordDao" \
  "ITaskDao"

# CreditRepository must not import DAOs outside its context.
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
  "IUserBehaviorRebateOrderDao" \
  "ITaskDao"

check_port_boundary() {
  local label="$1" repo_file="$2" port_name="$3" adapter_file="$4"
  if grep -q "$port_name" "$repo_file" 2>/dev/null; then
    pass "$label repository uses $port_name"
  else
    fail "$label repository does not use $port_name"
  fi
  if [[ -f "$adapter_file" ]] && grep -q "ITaskDao" "$adapter_file" 2>/dev/null; then
    pass "$label local adapter preserves ITaskDao fallback"
  else
    fail "$label local adapter missing ITaskDao fallback"
  fi
}

INFRA_PORT="$REPO_ROOT/big-market-infrastructure/src/main/java/com/dyx/market/infrastructure/adapter/port"
check_port_boundary "AL-8" "$INFRA_REPO/BehaviorRebateRepository.java" "IRebateTaskOutboxPort" "$INFRA_PORT/LocalRebateTaskOutboxPort.java"
check_port_boundary "AL-9" "$INFRA_REPO/CreditRepository.java" "ICreditTradeTaskOutboxPort" "$INFRA_PORT/LocalCreditTradeTaskOutboxPort.java"
check_port_boundary "AL-10" "$INFRA_REPO/AwardRepository.java" "IAwardDispatchTaskOutboxPort" "$INFRA_PORT/LocalAwardDispatchTaskOutboxPort.java"

# DispatchCreditAwardTaskJob must not import credit infra DAO directly.
if [[ -z "${DISPATCH_JOB:-}" || ! -f "$DISPATCH_JOB" ]]; then
  fail "DispatchCreditAwardTaskJob.java not found"
else
  if grep -q "ICreditAwardTaskDao" "$DISPATCH_JOB" 2>/dev/null; then
    fail "DispatchCreditAwardTaskJob forbidden DAO — ICreditAwardTaskDao direct import regressed"
  else
    pass "DispatchCreditAwardTaskJob forbidden DAO — no ICreditAwardTaskDao direct import"
  fi

  if grep -q "ICreditAwardTaskDispatchPort" "$DISPATCH_JOB" 2>/dev/null; then
    pass "DispatchCreditAwardTaskJob uses ICreditAwardTaskDispatchPort"
  else
    fail "DispatchCreditAwardTaskJob does not use ICreditAwardTaskDispatchPort"
  fi
fi

# ── 4. No new mapper XML ownership movement ───────────────────────────────────
echo ""
echo "── 4. No new mapper XML ownership movement ──"
# Canonical: mapper XMLs known to each service module.
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
  "award.service.remote-fulfillment.enabled"
  "strategy.service.remote-decision.enabled"
)

RESOURCE_DIRS=(
  "$REPO_ROOT/big-market-account-service/src/main/resources"
  "$REPO_ROOT/big-market-market-service/src/main/resources"
  "$REPO_ROOT/big-market-message-job-service/src/main/resources"
  "$REPO_ROOT/big-market-rebate-service/src/main/resources"
  "$REPO_ROOT/big-market-strategy-service/src/main/resources"
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
MASTER="$REPO_ROOT/docs/archive/microservices-history.md"
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
echo "  AL-5  AwardRepository    -> IUserRaffleOrderDao  [RESOLVED Phase 7-A prep AL-5]"
echo "  AL-6  AwardRepository    -> IUserCreditAccountDao  [RESOLVED Phase 7-A prep AL-6/AL-11]"
echo "  AL-7  DispatchCreditAwardTaskJob -> ICreditAwardTaskDao  [RESOLVED Phase 7-A prep AL-7]"
echo "  AL-8  BehaviorRebateRepository  -> ITaskDao  [RESOLVED Phase 7-C; local adapter fallback]"
echo "  AL-9  CreditRepository          -> ITaskDao  [RESOLVED Phase 7-C; local adapter fallback]"
echo "  AL-10 AwardRepository           -> ITaskDao  [RESOLVED Phase 7-C; local adapter fallback]"
echo "  AL-11 AwardRepository           -> ICreditAwardTaskDao  [RESOLVED Phase 7-A prep AL-6/AL-11]"
echo ""

if [[ "$FAIL" -eq 0 ]]; then
  echo "RESULT: ALL CHECKS PASSED — Phase 6-B package ownership boundary validator complete"
  exit 0
else
  echo "RESULT: $FAIL CHECK(S) FAILED — review output above"
  exit 1
fi

#!/usr/bin/env bash
# validate-microservices-phase-7-account-boundary-prep.sh
#
# Phase 7-A prep validator: account boundary port isolation.
#
# Asserts that:
#   AL-4 (ActivityRepository -> IUserCreditAccountDao) has been resolved by
#        routing credit-account reads through IActivityAccountPort.
#   AL-2 (StrategyRepository -> IRaffleActivityAccountDao) has been resolved by
#        routing total-use-count reads through IStrategyActivityAccountPort.
#   AL-3 (StrategyRepository -> IRaffleActivityAccountDayDao) has been resolved by
#        routing today-raffle-count reads through IStrategyActivityAccountPort.
#
# Hard constraints verified:
#   1. ActivityRepository no longer imports IUserCreditAccountDao.
#   2. IActivityAccountPort declares queryUserCreditAccountAmount.
#   3. LocalActivityAccountPort implements queryUserCreditAccountAmount and
#      delegates to IUserCreditAccountDao (local infra path, no remote call).
#   4. StrategyRepository no longer imports IRaffleActivityAccountDao or
#      IRaffleActivityAccountDayDao.
#   5. IStrategyActivityAccountPort declares queryTodayRaffleCount and queryTotalUseCount.
#   6. LocalStrategyActivityAccountPort implements both methods and delegates to
#      IRaffleActivityAccountDayDao / IRaffleActivityAccountDao.
#   7. No account remote-read/write flags are enabled.
#   8. Phase 6-B package-ownership validator passes.

set -uo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PASS=0
FAIL=0

pass() { echo "[PASS] $*"; PASS=$((PASS + 1)); }
fail() { echo "[FAIL] $*"; FAIL=$((FAIL + 1)); }

INFRA_REPO="$REPO_ROOT/big-market-infrastructure/src/main/java/com/dyx/market/infrastructure/adapter/repository"
INFRA_PORT="$REPO_ROOT/big-market-infrastructure/src/main/java/com/dyx/market/infrastructure/adapter/port"
DOMAIN_PORT="$REPO_ROOT/big-market-domain/src/main/java/com/dyx/market/domain/activity/adapter/port"
DOMAIN_STRATEGY_PORT="$REPO_ROOT/big-market-domain/src/main/java/com/dyx/market/domain/strategy/adapter/port"

echo ""
echo "========================================================================"
echo "  Phase 7-A Prep: Account Boundary Port Isolation Validator"
echo "  Repo: $REPO_ROOT"
echo "========================================================================"

# ── 1. ActivityRepository must NOT import IUserCreditAccountDao ───────────────
echo ""
echo "── 1. ActivityRepository direct credit-DAO access removed ──"

ACTIVITY_REPO="$INFRA_REPO/ActivityRepository.java"
if [[ ! -f "$ACTIVITY_REPO" ]]; then
  fail "ActivityRepository.java not found: $ACTIVITY_REPO"
else
  if grep -q "IUserCreditAccountDao" "$ACTIVITY_REPO" 2>/dev/null; then
    fail "ActivityRepository still references IUserCreditAccountDao — AL-4 not resolved"
  else
    pass "ActivityRepository does not reference IUserCreditAccountDao"
  fi

  if grep -q "userCreditAccountDao" "$ACTIVITY_REPO" 2>/dev/null; then
    fail "ActivityRepository still has userCreditAccountDao field — credit DAO field not removed"
  else
    pass "ActivityRepository has no userCreditAccountDao field"
  fi

  if grep -q "IActivityAccountPort" "$ACTIVITY_REPO" 2>/dev/null; then
    pass "ActivityRepository injects IActivityAccountPort (port seam wired)"
  else
    fail "ActivityRepository does not inject IActivityAccountPort — port seam missing"
  fi

  if grep -q "activityAccountPort.queryUserCreditAccountAmount\|activityAccountPort\.queryUserCreditAccountAmount" "$ACTIVITY_REPO" 2>/dev/null; then
    pass "ActivityRepository.queryUserCreditAccountAmount delegates to activityAccountPort"
  else
    fail "ActivityRepository.queryUserCreditAccountAmount does not delegate to activityAccountPort"
  fi
fi

# ── 2. IActivityAccountPort declares queryUserCreditAccountAmount ─────────────
echo ""
echo "── 2. IActivityAccountPort declares credit-account read method ──"

PORT_IFACE="$DOMAIN_PORT/IActivityAccountPort.java"
if [[ ! -f "$PORT_IFACE" ]]; then
  fail "IActivityAccountPort.java not found: $PORT_IFACE"
else
  if grep -q "queryUserCreditAccountAmount" "$PORT_IFACE" 2>/dev/null; then
    pass "IActivityAccountPort declares queryUserCreditAccountAmount"
  else
    fail "IActivityAccountPort does not declare queryUserCreditAccountAmount"
  fi

  if grep -q "BigDecimal" "$PORT_IFACE" 2>/dev/null; then
    pass "IActivityAccountPort.queryUserCreditAccountAmount returns BigDecimal"
  else
    fail "IActivityAccountPort missing BigDecimal return type for queryUserCreditAccountAmount"
  fi
fi

# ── 3. LocalActivityAccountPort delegates to IUserCreditAccountDao ────────────
echo ""
echo "── 3. LocalActivityAccountPort local implementation ──"

LOCAL_PORT="$INFRA_PORT/LocalActivityAccountPort.java"
if [[ ! -f "$LOCAL_PORT" ]]; then
  fail "LocalActivityAccountPort.java not found: $LOCAL_PORT"
else
  if grep -q "queryUserCreditAccountAmount" "$LOCAL_PORT" 2>/dev/null; then
    pass "LocalActivityAccountPort implements queryUserCreditAccountAmount"
  else
    fail "LocalActivityAccountPort does not implement queryUserCreditAccountAmount"
  fi

  if grep -q "IUserCreditAccountDao" "$LOCAL_PORT" 2>/dev/null; then
    pass "LocalActivityAccountPort injects IUserCreditAccountDao (local delegation)"
  else
    fail "LocalActivityAccountPort does not inject IUserCreditAccountDao"
  fi

  if grep -q "userCreditAccountDao.queryUserCreditAccount\|userCreditAccountDao\.queryUserCreditAccount" "$LOCAL_PORT" 2>/dev/null; then
    pass "LocalActivityAccountPort delegates to userCreditAccountDao.queryUserCreditAccount"
  else
    fail "LocalActivityAccountPort does not call userCreditAccountDao.queryUserCreditAccount"
  fi
fi

# ── 4. StrategyRepository must NOT import account quota DAOs (AL-2/AL-3) ──────
echo ""
echo "── 4. StrategyRepository direct account-quota DAO access removed ──"

STRATEGY_REPO="$INFRA_REPO/StrategyRepository.java"
if [[ ! -f "$STRATEGY_REPO" ]]; then
  fail "StrategyRepository.java not found: $STRATEGY_REPO"
else
  if grep -q "IRaffleActivityAccountDao" "$STRATEGY_REPO" 2>/dev/null; then
    fail "StrategyRepository still references IRaffleActivityAccountDao — AL-2 not resolved"
  else
    pass "StrategyRepository does not reference IRaffleActivityAccountDao"
  fi

  if grep -q "IRaffleActivityAccountDayDao" "$STRATEGY_REPO" 2>/dev/null; then
    fail "StrategyRepository still references IRaffleActivityAccountDayDao — AL-3 not resolved"
  else
    pass "StrategyRepository does not reference IRaffleActivityAccountDayDao"
  fi

  if grep -q "raffleActivityAccountDao\b" "$STRATEGY_REPO" 2>/dev/null; then
    fail "StrategyRepository still has raffleActivityAccountDao field — AL-2 not fully removed"
  else
    pass "StrategyRepository has no raffleActivityAccountDao field"
  fi

  if grep -q "raffleActivityAccountDayDao\b" "$STRATEGY_REPO" 2>/dev/null; then
    fail "StrategyRepository still has raffleActivityAccountDayDao field — AL-3 not fully removed"
  else
    pass "StrategyRepository has no raffleActivityAccountDayDao field"
  fi

  if grep -q "IStrategyActivityAccountPort" "$STRATEGY_REPO" 2>/dev/null; then
    pass "StrategyRepository injects IStrategyActivityAccountPort (port seam wired)"
  else
    fail "StrategyRepository does not inject IStrategyActivityAccountPort — port seam missing"
  fi

  if grep -q "strategyActivityAccountPort.queryTodayRaffleCount\|strategyActivityAccountPort\.queryTodayRaffleCount" "$STRATEGY_REPO" 2>/dev/null; then
    pass "StrategyRepository.queryTodayUserRaffleCount delegates to strategyActivityAccountPort"
  else
    fail "StrategyRepository.queryTodayUserRaffleCount does not delegate to strategyActivityAccountPort"
  fi

  if grep -q "strategyActivityAccountPort.queryTotalUseCount\|strategyActivityAccountPort\.queryTotalUseCount" "$STRATEGY_REPO" 2>/dev/null; then
    pass "StrategyRepository.queryActivityAccountTotalUseCount delegates to strategyActivityAccountPort"
  else
    fail "StrategyRepository.queryActivityAccountTotalUseCount does not delegate to strategyActivityAccountPort"
  fi
fi

# ── 5. IStrategyActivityAccountPort declares both read methods ────────────────
echo ""
echo "── 5. IStrategyActivityAccountPort declares account-quota read methods ──"

STRATEGY_PORT_IFACE="$DOMAIN_STRATEGY_PORT/IStrategyActivityAccountPort.java"
if [[ ! -f "$STRATEGY_PORT_IFACE" ]]; then
  fail "IStrategyActivityAccountPort.java not found: $STRATEGY_PORT_IFACE"
else
  if grep -q "queryTodayRaffleCount" "$STRATEGY_PORT_IFACE" 2>/dev/null; then
    pass "IStrategyActivityAccountPort declares queryTodayRaffleCount"
  else
    fail "IStrategyActivityAccountPort does not declare queryTodayRaffleCount"
  fi

  if grep -q "queryTotalUseCount" "$STRATEGY_PORT_IFACE" 2>/dev/null; then
    pass "IStrategyActivityAccountPort declares queryTotalUseCount"
  else
    fail "IStrategyActivityAccountPort does not declare queryTotalUseCount"
  fi
fi

# ── 6. LocalStrategyActivityAccountPort delegates to account-quota DAOs ───────
echo ""
echo "── 6. LocalStrategyActivityAccountPort local implementation ──"

LOCAL_STRATEGY_PORT="$INFRA_PORT/LocalStrategyActivityAccountPort.java"
if [[ ! -f "$LOCAL_STRATEGY_PORT" ]]; then
  fail "LocalStrategyActivityAccountPort.java not found: $LOCAL_STRATEGY_PORT"
else
  if grep -q "queryTodayRaffleCount" "$LOCAL_STRATEGY_PORT" 2>/dev/null; then
    pass "LocalStrategyActivityAccountPort implements queryTodayRaffleCount"
  else
    fail "LocalStrategyActivityAccountPort does not implement queryTodayRaffleCount"
  fi

  if grep -q "queryTotalUseCount" "$LOCAL_STRATEGY_PORT" 2>/dev/null; then
    pass "LocalStrategyActivityAccountPort implements queryTotalUseCount"
  else
    fail "LocalStrategyActivityAccountPort does not implement queryTotalUseCount"
  fi

  if grep -q "IRaffleActivityAccountDayDao" "$LOCAL_STRATEGY_PORT" 2>/dev/null; then
    pass "LocalStrategyActivityAccountPort injects IRaffleActivityAccountDayDao (AL-3 delegation)"
  else
    fail "LocalStrategyActivityAccountPort does not inject IRaffleActivityAccountDayDao"
  fi

  if grep -q "IRaffleActivityAccountDao" "$LOCAL_STRATEGY_PORT" 2>/dev/null; then
    pass "LocalStrategyActivityAccountPort injects IRaffleActivityAccountDao (AL-2 delegation)"
  else
    fail "LocalStrategyActivityAccountPort does not inject IRaffleActivityAccountDao"
  fi

  if grep -q "raffleActivityAccountDayDao.queryActivityAccountDayByUserId\|raffleActivityAccountDayDao\.queryActivityAccountDayByUserId" "$LOCAL_STRATEGY_PORT" 2>/dev/null; then
    pass "LocalStrategyActivityAccountPort delegates to raffleActivityAccountDayDao"
  else
    fail "LocalStrategyActivityAccountPort does not call raffleActivityAccountDayDao.queryActivityAccountDayByUserId"
  fi

  if grep -q "raffleActivityAccountDao.queryActivityAccountByUserId\|raffleActivityAccountDao\.queryActivityAccountByUserId" "$LOCAL_STRATEGY_PORT" 2>/dev/null; then
    pass "LocalStrategyActivityAccountPort delegates to raffleActivityAccountDao"
  else
    fail "LocalStrategyActivityAccountPort does not call raffleActivityAccountDao.queryActivityAccountByUserId"
  fi
fi

# ── 7. No account remote flags enabled ───────────────────────────────────────
echo ""
echo "── 7. Account remote flags still disabled ──"

REMOTE_FLAGS=(
  "account.remote-read.enabled"
  "account.remote-write.enabled"
  "account.service.remote-quota-decrement.enabled"
)

RESOURCE_DIRS=(
  "$REPO_ROOT/big-market-account-service/src/main/resources"
  "$REPO_ROOT/big-market-market-service/src/main/resources"
  "$REPO_ROOT/big-market-message-job-service/src/main/resources"
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
    pass "Flag safe: $flag"
  else
    fail "Flag appears enabled: $flag ($found match(es)) — do not enable remote traffic in prep batches"
  fi
done

# ── 8. Phase 6-B validator passes ────────────────────────────────────────────
echo ""
echo "── 8. Phase 6-B package-ownership validator ──"

PHASE6B_SCRIPT="$REPO_ROOT/scripts/validate-microservices-phase-6-package-ownership-boundaries.sh"
if [[ ! -f "$PHASE6B_SCRIPT" ]]; then
  fail "Phase 6-B validator not found: $PHASE6B_SCRIPT"
else
  if bash "$PHASE6B_SCRIPT" > /tmp/phase6b_output.txt 2>&1; then
    pass "Phase 6-B validator passed"
  else
    fail "Phase 6-B validator FAILED — see output below"
    cat /tmp/phase6b_output.txt
  fi
fi

# ── 9. DAO ownership doc updated for AL-2/AL-3/AL-4 ─────────────────────────
echo ""
echo "── 9. DAO ownership doc marks AL-2/AL-3/AL-4 resolved ──"

DAO_DOC="$REPO_ROOT/docs/microservices-dao-ownership.md"
if [[ ! -f "$DAO_DOC" ]]; then
  fail "docs/microservices-dao-ownership.md not found"
else
  if grep -q "RESOLVED\|resolved\|Phase 7-A prep" "$DAO_DOC" 2>/dev/null; then
    pass "docs/microservices-dao-ownership.md documents AL-4 resolution"
  else
    fail "docs/microservices-dao-ownership.md does not mention AL-4 resolution"
  fi
  if grep -q "IStrategyActivityAccountPort\|AL-2.*resolved\|AL-3.*resolved\|AL-2/AL-3" "$DAO_DOC" 2>/dev/null; then
    pass "docs/microservices-dao-ownership.md documents AL-2/AL-3 resolution"
  else
    fail "docs/microservices-dao-ownership.md does not mention AL-2/AL-3 resolution"
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
  echo "RESULT: ALL CHECKS PASSED — Phase 7-A account boundary prep complete"
  echo "        AL-4 (ActivityRepository -> IUserCreditAccountDao) is resolved."
  echo "        AL-2 (StrategyRepository -> IRaffleActivityAccountDao) is resolved."
  echo "        AL-3 (StrategyRepository -> IRaffleActivityAccountDayDao) is resolved."
  echo "        Next recommended batch: Phase 7-A AL-1 (StrategyRepository -> IRaffleActivityDao)"
  echo "        or Phase 7-B (generic task table strategy decision)."
  exit 0
else
  echo "RESULT: $FAIL CHECK(S) FAILED — review output above"
  exit 1
fi

#!/usr/bin/env bash
# validate-microservices-phase-7-credit-award-task-job-boundary.sh
#
# Phase 7-A prep validator: credit-award task job boundary (AL-7).
#
# Asserts that DispatchCreditAwardTaskJob no longer imports ICreditAwardTaskDao
# directly and routes credit_award_task reads/state transitions through
# ICreditAwardTaskDispatchPort. Repo-only static check; no external services.

set -uo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PASS=0
FAIL=0

pass() { echo "[PASS] $*"; PASS=$((PASS + 1)); }
fail() { echo "[FAIL] $*"; FAIL=$((FAIL + 1)); }

JOB_FILE="$REPO_ROOT/big-market-message-job-service/src/main/java/com/dyx/market/message/job/config/DispatchCreditAwardTaskJob.java"
PORT_IFACE="$REPO_ROOT/big-market-domain/src/main/java/com/dyx/market/domain/credit/adapter/port/ICreditAwardTaskDispatchPort.java"
TASK_ENTITY="$REPO_ROOT/big-market-domain/src/main/java/com/dyx/market/domain/credit/model/entity/CreditAwardTaskEntity.java"
LOCAL_PORT="$REPO_ROOT/big-market-infrastructure/src/main/java/com/dyx/market/infrastructure/adapter/port/LocalCreditAwardTaskDispatchPort.java"
AWARD_REPO="$REPO_ROOT/big-market-infrastructure/src/main/java/com/dyx/market/infrastructure/adapter/repository/AwardRepository.java"
PHASE6B_SCRIPT="$REPO_ROOT/scripts/validate-microservices-phase-6-package-ownership-boundaries.sh"

echo ""
echo "========================================================================"
echo "  Phase 7-A Prep: Credit Award Task Job Boundary Validator (AL-7)"
echo "  Repo: $REPO_ROOT"
echo "========================================================================"

# ── 1. DispatchCreditAwardTaskJob direct DAO access removed ──────────────────
echo ""
echo "── 1. DispatchCreditAwardTaskJob direct ICreditAwardTaskDao access removed ──"

if [[ ! -f "$JOB_FILE" ]]; then
  fail "DispatchCreditAwardTaskJob.java not found: $JOB_FILE"
else
  if grep -q "ICreditAwardTaskDao" "$JOB_FILE" 2>/dev/null; then
    fail "DispatchCreditAwardTaskJob still references ICreditAwardTaskDao — AL-7 not resolved"
  else
    pass "DispatchCreditAwardTaskJob does not reference ICreditAwardTaskDao"
  fi

  if grep -q "creditAwardTaskDao" "$JOB_FILE" 2>/dev/null; then
    fail "DispatchCreditAwardTaskJob still has creditAwardTaskDao field/calls"
  else
    pass "DispatchCreditAwardTaskJob has no creditAwardTaskDao field/calls"
  fi

  if grep -q "com.dyx.market.infrastructure.dao.po.CreditAwardTask" "$JOB_FILE" 2>/dev/null; then
    fail "DispatchCreditAwardTaskJob still imports infra CreditAwardTask PO"
  else
    pass "DispatchCreditAwardTaskJob does not import infra CreditAwardTask PO"
  fi
fi

# ── 2. DispatchCreditAwardTaskJob uses the new port ──────────────────────────
echo ""
echo "── 2. DispatchCreditAwardTaskJob port boundary wired ──"

if [[ -f "$JOB_FILE" ]]; then
  if grep -q "ICreditAwardTaskDispatchPort" "$JOB_FILE" 2>/dev/null; then
    pass "DispatchCreditAwardTaskJob imports/injects ICreditAwardTaskDispatchPort"
  else
    fail "DispatchCreditAwardTaskJob does not use ICreditAwardTaskDispatchPort"
  fi

  if grep -q "creditAwardTaskDispatchPort.queryPendingTasks" "$JOB_FILE" 2>/dev/null; then
    pass "DispatchCreditAwardTaskJob delegates pending query to port"
  else
    fail "DispatchCreditAwardTaskJob does not call creditAwardTaskDispatchPort.queryPendingTasks"
  fi

  if grep -q "creditAwardTaskDispatchPort.updateDispatched" "$JOB_FILE" 2>/dev/null; then
    pass "DispatchCreditAwardTaskJob delegates dispatched update to port"
  else
    fail "DispatchCreditAwardTaskJob does not call creditAwardTaskDispatchPort.updateDispatched"
  fi

  if grep -q "creditAwardTaskDispatchPort.updateRetryFailed" "$JOB_FILE" 2>/dev/null; then
    pass "DispatchCreditAwardTaskJob delegates retry update to port"
  else
    fail "DispatchCreditAwardTaskJob does not call creditAwardTaskDispatchPort.updateRetryFailed"
  fi

  if grep -q "CreditAwardTaskEntity" "$JOB_FILE" 2>/dev/null; then
    pass "DispatchCreditAwardTaskJob uses domain CreditAwardTaskEntity"
  else
    fail "DispatchCreditAwardTaskJob does not use domain CreditAwardTaskEntity"
  fi
fi

# ── 3. Port exposes only required operations ─────────────────────────────────
echo ""
echo "── 3. ICreditAwardTaskDispatchPort contract ──"

if [[ ! -f "$PORT_IFACE" ]]; then
  fail "ICreditAwardTaskDispatchPort.java not found: $PORT_IFACE"
else
  if grep -q "List<CreditAwardTaskEntity> queryPendingTasks()" "$PORT_IFACE" 2>/dev/null; then
    pass "Port exposes queryPendingTasks"
  else
    fail "Port missing queryPendingTasks signature"
  fi

  if grep -q "int updateDispatched(CreditAwardTaskEntity task)" "$PORT_IFACE" 2>/dev/null; then
    pass "Port exposes updateDispatched"
  else
    fail "Port missing updateDispatched signature"
  fi

  if grep -q "int updateRetryFailed(CreditAwardTaskEntity task)" "$PORT_IFACE" 2>/dev/null; then
    pass "Port exposes updateRetryFailed"
  else
    fail "Port missing updateRetryFailed signature"
  fi
fi

if [[ -f "$TASK_ENTITY" ]] && grep -q "class CreditAwardTaskEntity" "$TASK_ENTITY" 2>/dev/null; then
  pass "Domain CreditAwardTaskEntity exists"
else
  fail "Domain CreditAwardTaskEntity missing"
fi

# ── 4. Local implementation delegates to ICreditAwardTaskDao ─────────────────
echo ""
echo "── 4. LocalCreditAwardTaskDispatchPort delegation ──"

if [[ ! -f "$LOCAL_PORT" ]]; then
  fail "LocalCreditAwardTaskDispatchPort.java not found: $LOCAL_PORT"
else
  if grep -q "implements ICreditAwardTaskDispatchPort" "$LOCAL_PORT" 2>/dev/null; then
    pass "LocalCreditAwardTaskDispatchPort implements ICreditAwardTaskDispatchPort"
  else
    fail "LocalCreditAwardTaskDispatchPort does not implement ICreditAwardTaskDispatchPort"
  fi

  if grep -q "ICreditAwardTaskDao" "$LOCAL_PORT" 2>/dev/null; then
    pass "LocalCreditAwardTaskDispatchPort injects ICreditAwardTaskDao"
  else
    fail "LocalCreditAwardTaskDispatchPort does not inject ICreditAwardTaskDao"
  fi

  if grep -q "creditAwardTaskDao.queryPendingTasks()" "$LOCAL_PORT" 2>/dev/null; then
    pass "Local port delegates queryPendingTasks to DAO"
  else
    fail "Local port does not call creditAwardTaskDao.queryPendingTasks"
  fi

  if grep -q "creditAwardTaskDao.updateDispatched(toPo(task))" "$LOCAL_PORT" 2>/dev/null; then
    pass "Local port delegates updateDispatched to DAO"
  else
    fail "Local port does not call creditAwardTaskDao.updateDispatched"
  fi

  if grep -q "creditAwardTaskDao.updateRetryFailed(toPo(task))" "$LOCAL_PORT" 2>/dev/null; then
    pass "Local port delegates updateRetryFailed to DAO"
  else
    fail "Local port does not call creditAwardTaskDao.updateRetryFailed"
  fi
fi

# ── 5. Existing flag gate remains disabled by default ────────────────────────
echo ""
echo "── 5. account.award-credit-outbox flag gate ──"

if [[ -f "$JOB_FILE" ]] \
  && grep -q '@ConditionalOnProperty(name = "account.award-credit-outbox.enabled", havingValue = "true")' "$JOB_FILE" 2>/dev/null; then
  pass "DispatchCreditAwardTaskJob remains @ConditionalOnProperty(account.award-credit-outbox.enabled=true) guarded"
else
  fail "DispatchCreditAwardTaskJob conditional flag gate changed or missing"
fi

FLAG_FILES=(
  "$REPO_ROOT/big-market-message-job-service/src/main/resources/application.yml"
  "$REPO_ROOT/big-market-fulfillment-service/src/main/resources/application.yml"
  "$REPO_ROOT/big-market-app/src/main/resources/application-dev.yml"
  "$REPO_ROOT/docker-compose.yml"
)

enabled_found=0
for file in "${FLAG_FILES[@]}"; do
  cnt=$(grep -nE 'ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED(:-|:|=)?true|award-credit-outbox\.enabled[[:space:]]*:[[:space:]]*true' "$file" 2>/dev/null \
    | grep -cv '^[[:space:]]*#' || true)
  enabled_found=$((enabled_found + cnt))
done

if [[ "$enabled_found" -eq 0 ]]; then
  pass "account.award-credit-outbox.enabled remains default false"
else
  fail "account.award-credit-outbox.enabled appears default true ($enabled_found match(es))"
fi

# ── 6. AwardRepository credit couplings route through port ───────────────────
echo ""
echo "── 6. AwardRepository credit couplings route through port ──"

if [[ -f "$AWARD_REPO" ]]; then
  if grep -q "IAwardCreditWritePort" "$AWARD_REPO" 2>/dev/null \
    && grep -q "awardCreditWritePort.updateOrCreateCreditAccount" "$AWARD_REPO" 2>/dev/null; then
    pass "AL-6 resolved: AwardRepository routes credit-account write through IAwardCreditWritePort"
  else
    fail "AL-6 port boundary not found in AwardRepository"
  fi

  if grep -q "awardCreditWritePort.insertCreditAwardTask" "$AWARD_REPO" 2>/dev/null; then
    pass "AL-11 resolved: AwardRepository routes credit_award_task insert through IAwardCreditWritePort"
  else
    fail "AL-11 port boundary not found in AwardRepository"
  fi
fi

# ── 7. Phase 6-B validator passes ────────────────────────────────────────────
echo ""
echo "── 7. Phase 6-B package-ownership validator ──"

if [[ ! -f "$PHASE6B_SCRIPT" ]]; then
  fail "Phase 6-B validator not found: $PHASE6B_SCRIPT"
else
  if bash "$PHASE6B_SCRIPT" > /tmp/phase6b_al7_output.txt 2>&1; then
    pass "Phase 6-B validator passed"
  else
    fail "Phase 6-B validator FAILED — see output below"
    cat /tmp/phase6b_al7_output.txt
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
  echo "RESULT: ALL CHECKS PASSED — Phase 7-A AL-7 credit-award task job boundary complete"
  echo "        AL-7 (DispatchCreditAwardTaskJob -> ICreditAwardTaskDao) is RESOLVED."
  echo "        DispatchCreditAwardTaskJob now routes outbox reads/state transitions through ICreditAwardTaskDispatchPort."
  echo "        AL-6 and AL-11 route through IAwardCreditWritePort."
  exit 0
else
  echo "RESULT: $FAIL CHECK(S) FAILED — review output above"
  exit 1
fi

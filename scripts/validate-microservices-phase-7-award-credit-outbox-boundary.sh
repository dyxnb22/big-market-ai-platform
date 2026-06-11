#!/usr/bin/env bash
# validate-microservices-phase-7-award-credit-outbox-boundary.sh
#
# Phase 7-A prep validator: AwardRepository credit outbox/write boundary
# (AL-6 / AL-11).
#
# Asserts that AwardRepository no longer imports credit DAOs directly and routes
# both the default credit-account write and the flag-gated credit_award_task
# outbox insert through IAwardCreditWritePort. Repo-only static check; no
# external services.

set -uo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PASS=0
FAIL=0

pass() { echo "[PASS] $*"; PASS=$((PASS + 1)); }
fail() { echo "[FAIL] $*"; FAIL=$((FAIL + 1)); }

AWARD_REPO="$REPO_ROOT/big-market-infrastructure/src/main/java/com/dyx/market/infrastructure/adapter/repository/AwardRepository.java"
PORT_IFACE="$REPO_ROOT/big-market-domain/src/main/java/com/dyx/market/domain/award/adapter/port/IAwardCreditWritePort.java"
LOCAL_PORT="$REPO_ROOT/big-market-infrastructure/src/main/java/com/dyx/market/infrastructure/adapter/port/LocalAwardCreditWritePort.java"
PHASE6B_SCRIPT="$REPO_ROOT/scripts/validate-microservices-phase-6-package-ownership-boundaries.sh"
DAO_DOC="$REPO_ROOT/docs/microservices-dao-ownership.md"

echo ""
echo "========================================================================"
echo "  Phase 7-A Prep: Award Credit Outbox/Write Boundary Validator (AL-6/AL-11)"
echo "  Repo: $REPO_ROOT"
echo "========================================================================"

# ── 1. AwardRepository direct credit DAO access removed ──────────────────────
echo ""
echo "── 1. AwardRepository direct credit DAO access removed ──"

if [[ ! -f "$AWARD_REPO" ]]; then
  fail "AwardRepository.java not found: $AWARD_REPO"
else
  if grep -q "IUserCreditAccountDao" "$AWARD_REPO" 2>/dev/null; then
    fail "AwardRepository still references IUserCreditAccountDao — AL-6 not resolved"
  else
    pass "AwardRepository does not reference IUserCreditAccountDao"
  fi

  if grep -q "userCreditAccountDao" "$AWARD_REPO" 2>/dev/null; then
    fail "AwardRepository still has userCreditAccountDao field/calls"
  else
    pass "AwardRepository has no userCreditAccountDao field/calls"
  fi

  if grep -q "ICreditAwardTaskDao" "$AWARD_REPO" 2>/dev/null; then
    fail "AwardRepository still references ICreditAwardTaskDao — AL-11 not resolved"
  else
    pass "AwardRepository does not reference ICreditAwardTaskDao"
  fi

  if grep -q "creditAwardTaskDao" "$AWARD_REPO" 2>/dev/null; then
    fail "AwardRepository still has creditAwardTaskDao field/calls"
  else
    pass "AwardRepository has no creditAwardTaskDao field/calls"
  fi

  if grep -qE "com\\.dyx\\.market\\.infrastructure\\.dao\\.po\\.(UserCreditAccount|CreditAwardTask)|\\bnew UserCreditAccount\\b|\\bnew CreditAwardTask\\b" "$AWARD_REPO" 2>/dev/null; then
    fail "AwardRepository still builds credit infra PO directly"
  else
    pass "AwardRepository does not build UserCreditAccount/CreditAwardTask POs"
  fi
fi

# ── 2. AwardRepository uses IAwardCreditWritePort ────────────────────────────
echo ""
echo "── 2. AwardRepository credit write port wired ──"

if [[ -f "$AWARD_REPO" ]]; then
  if grep -q "IAwardCreditWritePort" "$AWARD_REPO" 2>/dev/null; then
    pass "AwardRepository imports/injects IAwardCreditWritePort"
  else
    fail "AwardRepository does not use IAwardCreditWritePort"
  fi

  if grep -q "awardCreditWritePort.updateOrCreateCreditAccount" "$AWARD_REPO" 2>/dev/null; then
    pass "AwardRepository delegates default credit-account write to port"
  else
    fail "AwardRepository does not call awardCreditWritePort.updateOrCreateCreditAccount"
  fi

  if grep -q "awardCreditWritePort.insertCreditAwardTask" "$AWARD_REPO" 2>/dev/null; then
    pass "AwardRepository delegates credit_award_task outbox insert to port"
  else
    fail "AwardRepository does not call awardCreditWritePort.insertCreditAwardTask"
  fi

  if grep -q "transactionTemplate.execute" "$AWARD_REPO" 2>/dev/null \
    && grep -q "dbRouter.doRouter(giveOutPrizesAggregate.getUserId())" "$AWARD_REPO" 2>/dev/null \
    && grep -q "lock.lock(3, TimeUnit.SECONDS)" "$AWARD_REPO" 2>/dev/null; then
    pass "AwardRepository still owns lock, dbRouter, and transactionTemplate boundary"
  else
    fail "AwardRepository lock/routing/transaction boundary changed or not found"
  fi
fi

# ── 3. Port interface exposes only required write operations ─────────────────
echo ""
echo "── 3. IAwardCreditWritePort contract ──"

if [[ ! -f "$PORT_IFACE" ]]; then
  fail "IAwardCreditWritePort.java not found: $PORT_IFACE"
else
  if grep -q "void updateOrCreateCreditAccount(String userId, BigDecimal creditAmount)" "$PORT_IFACE" 2>/dev/null; then
    pass "Port exposes updateOrCreateCreditAccount(userId, creditAmount)"
  else
    fail "Port missing updateOrCreateCreditAccount signature"
  fi

  if grep -q "void insertCreditAwardTask(String userId, String awardOrderId, BigDecimal creditAmount)" "$PORT_IFACE" 2>/dev/null; then
    pass "Port exposes insertCreditAwardTask(userId, awardOrderId, creditAmount)"
  else
    fail "Port missing insertCreditAwardTask signature"
  fi

  if grep -q "IUserCreditAccountDao" "$PORT_IFACE" 2>/dev/null \
    && grep -q "ICreditAwardTaskDao" "$PORT_IFACE" 2>/dev/null; then
    pass "Port documents AL-6 and AL-11 DAO isolation"
  else
    fail "Port does not document the DAO dependencies it isolates"
  fi
fi

# ── 4. Local implementation delegates to existing DAOs ───────────────────────
echo ""
echo "── 4. LocalAwardCreditWritePort delegation ──"

if [[ ! -f "$LOCAL_PORT" ]]; then
  fail "LocalAwardCreditWritePort.java not found: $LOCAL_PORT"
else
  if grep -q "implements IAwardCreditWritePort" "$LOCAL_PORT" 2>/dev/null; then
    pass "LocalAwardCreditWritePort implements IAwardCreditWritePort"
  else
    fail "LocalAwardCreditWritePort does not implement IAwardCreditWritePort"
  fi

  if grep -q "IUserCreditAccountDao" "$LOCAL_PORT" 2>/dev/null \
    && grep -q "ICreditAwardTaskDao" "$LOCAL_PORT" 2>/dev/null; then
    pass "Local port injects existing credit DAOs"
  else
    fail "Local port does not inject both existing credit DAOs"
  fi

  if grep -q "userCreditAccountDao.queryUserCreditAccount(userCreditAccountReq)" "$LOCAL_PORT" 2>/dev/null \
    && grep -q "userCreditAccountDao.insert(userCreditAccountReq)" "$LOCAL_PORT" 2>/dev/null \
    && grep -q "userCreditAccountDao.updateAddAmount(userCreditAccountReq)" "$LOCAL_PORT" 2>/dev/null; then
    pass "Local port preserves query/insert/updateAddAmount credit-account semantics"
  else
    fail "Local port does not delegate expected credit-account DAO calls"
  fi

  if grep -q "req.setTotalAmount(creditAmount)" "$LOCAL_PORT" 2>/dev/null \
    && grep -q "req.setAvailableAmount(creditAmount)" "$LOCAL_PORT" 2>/dev/null \
    && grep -q "req.setAccountStatus(AccountStatusVO.open.getCode())" "$LOCAL_PORT" 2>/dev/null; then
    pass "Local port preserves credit-account request defaults"
  else
    fail "Local port does not preserve credit-account request defaults"
  fi

  if grep -q "creditAwardTaskDao.insert(task)" "$LOCAL_PORT" 2>/dev/null \
    && grep -q "task.setUserId(userId)" "$LOCAL_PORT" 2>/dev/null \
    && grep -q "task.setAwardOrderId(awardOrderId)" "$LOCAL_PORT" 2>/dev/null \
    && grep -q "task.setCreditAmount(creditAmount)" "$LOCAL_PORT" 2>/dev/null; then
    pass "Local port preserves credit_award_task insert fields"
  else
    fail "Local port does not delegate expected credit_award_task insert"
  fi
fi

# ── 5. Feature flag defaults remain unchanged ────────────────────────────────
echo ""
echo "── 5. Feature flag defaults ──"

if [[ -f "$AWARD_REPO" ]] \
  && grep -q '@Value("${account.award-credit-outbox.enabled:false}")' "$AWARD_REPO" 2>/dev/null; then
  pass "AwardRepository keeps account.award-credit-outbox.enabled default false"
else
  fail "AwardRepository award-credit-outbox flag default changed or missing"
fi

enabled_found=$(grep -RInE 'ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED(:-|:|=)?true|award-credit-outbox\.enabled[[:space:]]*:[[:space:]]*true' \
  "$REPO_ROOT"/big-market-*/src/main/resources "$REPO_ROOT/docker-compose.yml" 2>/dev/null \
  | grep -cv '^[[:space:]]*#' || true)

if [[ "$enabled_found" -eq 0 ]]; then
  pass "account.award-credit-outbox.enabled remains default false in resources/compose"
else
  fail "account.award-credit-outbox.enabled appears default true ($enabled_found match(es))"
fi

# ── 6. AL-6 / AL-11 marked resolved only after direct deps removed ───────────
echo ""
echo "── 6. AL-6 / AL-11 resolution docs are honest ──"

if [[ -f "$DAO_DOC" ]] \
  && grep -q "AL-6 resolved" "$DAO_DOC" 2>/dev/null \
  && grep -q "AL-11 resolved" "$DAO_DOC" 2>/dev/null; then
  if [[ -f "$AWARD_REPO" ]] \
    && ! grep -q "IUserCreditAccountDao\\|userCreditAccountDao\\|ICreditAwardTaskDao\\|creditAwardTaskDao" "$AWARD_REPO" 2>/dev/null; then
    pass "Docs mark AL-6/AL-11 resolved and AwardRepository direct DAO dependencies are removed"
  else
    fail "Docs mark AL-6/AL-11 resolved while AwardRepository still has direct DAO dependencies"
  fi
else
  fail "DAO ownership doc does not mark both AL-6 and AL-11 resolved"
fi

# ── 7. Phase 6-B validator passes ────────────────────────────────────────────
echo ""
echo "── 7. Phase 6-B package-ownership validator ──"

if [[ ! -f "$PHASE6B_SCRIPT" ]]; then
  fail "Phase 6-B validator not found: $PHASE6B_SCRIPT"
else
  if bash "$PHASE6B_SCRIPT" > /tmp/phase6b_al6_al11_output.txt 2>&1; then
    pass "Phase 6-B validator passed"
  else
    fail "Phase 6-B validator FAILED — see output below"
    cat /tmp/phase6b_al6_al11_output.txt
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
  echo "RESULT: ALL CHECKS PASSED — Phase 7-A AL-6/AL-11 award credit outbox/write boundary complete"
  echo "        AL-6 (AwardRepository -> IUserCreditAccountDao) is RESOLVED."
  echo "        AL-11 (AwardRepository -> ICreditAwardTaskDao) is RESOLVED."
  echo "        AwardRepository now routes credit writes through IAwardCreditWritePort."
  exit 0
else
  echo "RESULT: $FAIL CHECK(S) FAILED — review output above"
  exit 1
fi

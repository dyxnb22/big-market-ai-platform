#!/usr/bin/env bash
# validate-microservices-phase-7-award-activity-order-boundary.sh
#
# Phase 7-A prep validator: award -> activity order boundary (AL-5).
#
# Asserts that AwardRepository no longer imports IUserRaffleOrderDao directly
# and routes the guarded user_raffle_order create->used transition through
# IAwardActivityOrderPort. Repo-only static check; no external services.

set -uo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PASS=0
FAIL=0

pass() { echo "[PASS] $*"; PASS=$((PASS + 1)); }
fail() { echo "[FAIL] $*"; FAIL=$((FAIL + 1)); }

INFRA_REPO="$REPO_ROOT/big-market-infrastructure/src/main/java/com/dyx/market/infrastructure/adapter/repository"
INFRA_PORT="$REPO_ROOT/big-market-infrastructure/src/main/java/com/dyx/market/infrastructure/adapter/port"
DOMAIN_AWARD_PORT="$REPO_ROOT/big-market-domain/src/main/java/com/dyx/market/domain/award/adapter/port"

AWARD_REPO="$INFRA_REPO/AwardRepository.java"
PORT_IFACE="$DOMAIN_AWARD_PORT/IAwardActivityOrderPort.java"
LOCAL_PORT="$INFRA_PORT/LocalAwardActivityOrderPort.java"
PHASE6B_SCRIPT="$REPO_ROOT/scripts/validate-microservices-phase-6-package-ownership-boundaries.sh"

echo ""
echo "========================================================================"
echo "  Phase 7-A Prep: Award Activity-Order Boundary Validator (AL-5)"
echo "  Repo: $REPO_ROOT"
echo "========================================================================"

# ── 1. AwardRepository direct activity-order DAO access removed ──────────────
echo ""
echo "── 1. AwardRepository direct IUserRaffleOrderDao access removed ──"

if [[ ! -f "$AWARD_REPO" ]]; then
  fail "AwardRepository.java not found: $AWARD_REPO"
else
  if grep -q "IUserRaffleOrderDao" "$AWARD_REPO" 2>/dev/null; then
    fail "AwardRepository still references IUserRaffleOrderDao — AL-5 not resolved"
  else
    pass "AwardRepository does not reference IUserRaffleOrderDao"
  fi

  if grep -q "userRaffleOrderDao" "$AWARD_REPO" 2>/dev/null; then
    fail "AwardRepository still has userRaffleOrderDao field"
  else
    pass "AwardRepository has no userRaffleOrderDao field"
  fi

  if grep -qE "\bUserRaffleOrder\b" "$AWARD_REPO" 2>/dev/null; then
    fail "AwardRepository still builds UserRaffleOrder PO directly"
  else
    pass "AwardRepository does not reference UserRaffleOrder PO"
  fi
fi

# ── 2. AwardRepository uses IAwardActivityOrderPort ──────────────────────────
echo ""
echo "── 2. AwardRepository port seam wired ──"

if [[ -f "$AWARD_REPO" ]]; then
  if grep -q "IAwardActivityOrderPort" "$AWARD_REPO" 2>/dev/null; then
    pass "AwardRepository imports/injects IAwardActivityOrderPort"
  else
    fail "AwardRepository does not use IAwardActivityOrderPort"
  fi

  if grep -q "awardActivityOrderPort.markUserRaffleOrderUsed" "$AWARD_REPO" 2>/dev/null; then
    pass "AwardRepository delegates raffle-order state transition to awardActivityOrderPort"
  else
    fail "AwardRepository does not call awardActivityOrderPort.markUserRaffleOrderUsed"
  fi

  if grep -q "transactionTemplate.execute" "$AWARD_REPO" 2>/dev/null \
    && grep -q "dbRouter.doRouter(userId)" "$AWARD_REPO" 2>/dev/null; then
    pass "AwardRepository still owns dbRouter and transactionTemplate boundary"
  else
    fail "AwardRepository transaction/routing boundary not found"
  fi
fi

# ── 3. Port interface exposes the required narrow method ─────────────────────
echo ""
echo "── 3. IAwardActivityOrderPort contract ──"

if [[ ! -f "$PORT_IFACE" ]]; then
  fail "IAwardActivityOrderPort.java not found: $PORT_IFACE"
else
  if grep -q "int markUserRaffleOrderUsed(String userId, String orderId)" "$PORT_IFACE" 2>/dev/null; then
    pass "IAwardActivityOrderPort exposes markUserRaffleOrderUsed(userId, orderId)"
  else
    fail "IAwardActivityOrderPort missing required markUserRaffleOrderUsed signature"
  fi

  if grep -q "IUserRaffleOrderDao" "$PORT_IFACE" 2>/dev/null; then
    pass "IAwardActivityOrderPort documents the AL-5 DAO it isolates"
  else
    fail "IAwardActivityOrderPort does not document IUserRaffleOrderDao isolation"
  fi
fi

# ── 4. Local implementation delegates to IUserRaffleOrderDao ─────────────────
echo ""
echo "── 4. LocalAwardActivityOrderPort delegation ──"

if [[ ! -f "$LOCAL_PORT" ]]; then
  fail "LocalAwardActivityOrderPort.java not found: $LOCAL_PORT"
else
  if grep -q "implements IAwardActivityOrderPort" "$LOCAL_PORT" 2>/dev/null; then
    pass "LocalAwardActivityOrderPort implements IAwardActivityOrderPort"
  else
    fail "LocalAwardActivityOrderPort does not implement IAwardActivityOrderPort"
  fi

  if grep -q "IUserRaffleOrderDao" "$LOCAL_PORT" 2>/dev/null; then
    pass "LocalAwardActivityOrderPort injects IUserRaffleOrderDao"
  else
    fail "LocalAwardActivityOrderPort does not inject IUserRaffleOrderDao"
  fi

  if grep -q "new UserRaffleOrder" "$LOCAL_PORT" 2>/dev/null \
    && grep -q "setUserId(userId)" "$LOCAL_PORT" 2>/dev/null \
    && grep -q "setOrderId(orderId)" "$LOCAL_PORT" 2>/dev/null; then
    pass "LocalAwardActivityOrderPort builds the same UserRaffleOrder request fields"
  else
    fail "LocalAwardActivityOrderPort does not build expected UserRaffleOrder request"
  fi

  if grep -q "userRaffleOrderDao.updateUserRaffleOrderStateUsed(userRaffleOrderReq)" "$LOCAL_PORT" 2>/dev/null; then
    pass "LocalAwardActivityOrderPort delegates to updateUserRaffleOrderStateUsed"
  else
    fail "LocalAwardActivityOrderPort does not call updateUserRaffleOrderStateUsed"
  fi
fi

# ── 5. AL-6 and AL-11 remain unchanged ───────────────────────────────────────
echo ""
echo "── 5. Remaining award credit couplings unchanged ──"

if [[ -f "$AWARD_REPO" ]]; then
  if grep -q "IUserCreditAccountDao" "$AWARD_REPO" 2>/dev/null \
    && grep -q "userCreditAccountDao" "$AWARD_REPO" 2>/dev/null; then
    pass "AL-6 remains present: AwardRepository -> IUserCreditAccountDao"
  else
    fail "AL-6 changed unexpectedly — IUserCreditAccountDao path not found"
  fi

  if grep -q "ICreditAwardTaskDao" "$AWARD_REPO" 2>/dev/null \
    && grep -q "creditAwardTaskDao" "$AWARD_REPO" 2>/dev/null; then
    pass "AL-11 remains present: AwardRepository -> ICreditAwardTaskDao"
  else
    fail "AL-11 changed unexpectedly — ICreditAwardTaskDao path not found"
  fi
fi

# ── 6. Remote / production flags remain disabled ─────────────────────────────
echo ""
echo "── 6. Remote / production flag defaults ──"

REMOTE_FLAGS=(
  "account.remote-read.enabled"
  "account.remote-write.enabled"
  "account.award-credit-outbox.enabled"
  "account.service.remote-quota-decrement.enabled"
  "rebate.remote-create-order.enabled"
  "rebate.service.remote-read.enabled"
  "strategy.service.remote-read.enabled"
  "strategy.service.remote-decision.enabled"
  "fulfillment.remote.enabled"
  "activity.service.remote-draw.enabled"
  "activity.service.remote-strategy-mapping.enabled"
  "award.service.remote-fulfillment.enabled"
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
    cnt=$(grep -RInE "${flag}([[:space:]]*:|=)[[:space:]]*true" "$dir" \
      --include='*.yml' --include='*.yaml' --include='*.properties' \
      2>/dev/null | grep -cv '^[^:]*:[[:space:]]*#' || true)
    found=$((found + cnt))
  done
  dc_cnt=$(grep -nE "${flag}.*true" "$REPO_ROOT/docker-compose.yml" 2>/dev/null \
    | grep -cv '^[[:space:]]*#' || true)
  found=$((found + dc_cnt))

  if [[ "$found" -eq 0 ]]; then
    pass "Flag default safe: $flag"
  else
    fail "Flag appears enabled: $flag ($found match(es))"
  fi
done

# ── 7. Phase 6-B validator passes ────────────────────────────────────────────
echo ""
echo "── 7. Phase 6-B package-ownership validator ──"

if [[ ! -f "$PHASE6B_SCRIPT" ]]; then
  fail "Phase 6-B validator not found: $PHASE6B_SCRIPT"
else
  if bash "$PHASE6B_SCRIPT" > /tmp/phase6b_al5_output.txt 2>&1; then
    pass "Phase 6-B validator passed"
  else
    fail "Phase 6-B validator FAILED — see output below"
    cat /tmp/phase6b_al5_output.txt
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
  echo "RESULT: ALL CHECKS PASSED — Phase 7-A AL-5 award activity-order boundary complete"
  echo "        AL-5 (AwardRepository -> IUserRaffleOrderDao) is RESOLVED."
  echo "        AwardRepository now routes user_raffle_order state transitions through IAwardActivityOrderPort."
  echo "        AL-6 and AL-11 remain unchanged."
  exit 0
else
  echo "RESULT: $FAIL CHECK(S) FAILED — review output above"
  exit 1
fi

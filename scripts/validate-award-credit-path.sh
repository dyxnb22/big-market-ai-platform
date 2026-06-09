#!/usr/bin/env bash
# Phase 2.2-B4: Static audit of the award credit write path.
#
# Checks:
#   1. AwardRepository still directly writes user_credit_account (not routed through adapter).
#   2. UserCreditRandomAward does NOT reference ICreditAdjustService.
#   3. saveGiveOutPrizesAggregate contains both credit-account and award-record writes.
#   4. Both writes are inside a transactionTemplate block (same transaction).
#
# None of these checks require a running stack. They verify source-code invariants.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
AWARD_REPO="$REPO_ROOT/big-market-infrastructure/src/main/java/com/dyx/market/infrastructure/adapter/repository/AwardRepository.java"
USER_CREDIT_AWARD="$REPO_ROOT/big-market-domain/src/main/java/com/dyx/market/domain/award/service/distribute/impl/UserCreditRandomAward.java"
TX_BLOCK=""

PASS=0
FAIL=0

check_pass() { echo "[PASS] $1"; PASS=$((PASS + 1)); }
check_fail() { echo "[FAIL] $1"; FAIL=$((FAIL + 1)); }

echo "=== Phase 2.2-B4 Award Credit Path Static Audit ==="
echo ""

# --- Check 1: AwardRepository exists ---
if [[ -f "$AWARD_REPO" ]]; then
    check_pass "AwardRepository.java found at expected path"
    TX_BLOCK="$(awk '
        /void saveGiveOutPrizesAggregate/ { in_method = 1 }
        in_method && /transactionTemplate\.execute\(status -> \{/ { capture = 1 }
        capture { print }
        capture && /^            \}\);/ { exit }
    ' "$AWARD_REPO")"
else
    check_fail "AwardRepository.java not found at $AWARD_REPO"
fi

# --- Check 2: AwardRepository directly uses IUserCreditAccountDao (not adapter) ---
if grep -q "userCreditAccountDao" "$AWARD_REPO"; then
    check_pass "AwardRepository.saveGiveOutPrizesAggregate directly uses userCreditAccountDao (not routed through remote adapter)"
else
    check_fail "AwardRepository does NOT reference userCreditAccountDao — credit write path may have changed"
fi

# --- Check 3: AwardRepository still has direct award-record update ---
if grep -q "userAwardRecordDao" "$AWARD_REPO"; then
    check_pass "AwardRepository.saveGiveOutPrizesAggregate references userAwardRecordDao (award-record update present)"
else
    check_fail "AwardRepository does NOT reference userAwardRecordDao — award-record update may have been removed"
fi

# --- Check 4: Both writes are inside transactionTemplate ---
if [[ "$TX_BLOCK" == *"transactionTemplate.execute"* ]] \
    && [[ "$TX_BLOCK" == *"updateOrCreateCreditAccount"* ]] \
    && [[ "$TX_BLOCK" == *"updateAwardRecordCompletedState"* ]]; then
    check_pass "Credit-account write and award-record write are both inside transactionTemplate"
else
    check_fail "Could not prove both writes are inside transactionTemplate — transaction boundary may have changed"
fi

# --- Check 5: UserCreditRandomAward does NOT import ICreditAdjustService ---
if grep -q "ICreditAdjustService" "$USER_CREDIT_AWARD"; then
    check_fail "UserCreditRandomAward references ICreditAdjustService — this contradicts the B4 audit finding; documentation may need updating"
else
    check_pass "UserCreditRandomAward does NOT reference ICreditAdjustService (confirmed: award path bypasses ICreditAdjustService)"
fi

# --- Check 6: UserCreditRandomAward calls saveGiveOutPrizesAggregate ---
if grep -q "saveGiveOutPrizesAggregate" "$USER_CREDIT_AWARD"; then
    check_pass "UserCreditRandomAward calls repository.saveGiveOutPrizesAggregate (confirmed call chain)"
else
    check_fail "UserCreditRandomAward does NOT call saveGiveOutPrizesAggregate — call chain may have changed"
fi

# --- Check 7: IAccountCreditWriteAdapter is NOT referenced in AwardRepository (not wired yet) ---
if grep -q "IAccountCreditWriteAdapter\|accountCreditWriteAdapter" "$AWARD_REPO"; then
    check_fail "AwardRepository references IAccountCreditWriteAdapter — remote adapter was wired without saga/outbox strategy; transaction-boundary risk"
else
    check_pass "AwardRepository does NOT reference IAccountCreditWriteAdapter (intentionally deferred — see Phase 2.2-B4 design note)"
fi

# --- Check 8: IAccountCreditWriteAdapter is NOT referenced in UserCreditRandomAward ---
if grep -q "IAccountCreditWriteAdapter\|accountCreditWriteAdapter" "$USER_CREDIT_AWARD"; then
    check_fail "UserCreditRandomAward references IAccountCreditWriteAdapter — remote adapter was wired without saga/outbox strategy"
else
    check_pass "UserCreditRandomAward does NOT reference IAccountCreditWriteAdapter (intentionally deferred)"
fi

echo ""
echo "=== Summary ==="
echo "PASS: $PASS  FAIL: $FAIL"
echo ""
echo "Known pending work (not a test failure):"
echo "  - Award credit remote write needs transactional strategy (saga or outbox) before wiring"
echo "  - RaffleActivityPartakeService quota decrement: deferred, high risk"
echo "  - MQ idempotency and business-flow validation required before enabling write flags"
echo ""

if [[ "$FAIL" -gt 0 ]]; then
    echo "RESULT: FAIL — $FAIL check(s) failed. Review output above."
    exit 1
else
    echo "RESULT: PASS — all $PASS checks passed."
    exit 0
fi

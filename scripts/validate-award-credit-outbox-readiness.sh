#!/usr/bin/env bash
# Phase 2.2-B5: Static audit of award credit outbox readiness.
#
# Checks:
#   1. AwardRepository does NOT reference IAccountCreditWriteAdapter (wiring still deferred).
#   2. Both credit-account and award-record writes remain inside transactionTemplate.execute()
#      in saveGiveOutPrizesAggregate (transaction boundary unchanged).
#   3. docs/archive/phases.md contains the B5 outbox strategy section.
#   4. That doc explicitly forbids direct remote adapter wiring before outbox/saga.
#   5. docs/sql/proposed-credit-award-task-outbox.sql exists.
#   6. The SQL DDL contains a UNIQUE constraint on award_order_id (idempotency key present).
#   7. No production Java source (src/main/java) references credit_award_task (wiring deferred).
#   8. AwardRepository still uses userCreditAccountDao (direct write path unchanged).
#
# None of these checks require a running stack. They verify source-code and doc invariants.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
AWARD_REPO="$REPO_ROOT/big-market-infrastructure/src/main/java/com/dyx/market/infrastructure/adapter/repository/AwardRepository.java"
DESIGN_DOC="$REPO_ROOT/docs/archive/phases.md"
DDL_FILE="$REPO_ROOT/docs/sql/proposed-credit-award-task-outbox.sql"

PASS=0
FAIL=0

check_pass() { echo "[PASS] $1"; PASS=$((PASS + 1)); }
check_fail() { echo "[FAIL] $1"; FAIL=$((FAIL + 1)); }

echo "=== Phase 2.2-B5 Award Credit Outbox Readiness Static Audit ==="
echo ""

# --- Check 1: AwardRepository does NOT reference IAccountCreditWriteAdapter ---
if grep -q "IAccountCreditWriteAdapter\|accountCreditWriteAdapter" "$AWARD_REPO"; then
    check_fail "AwardRepository references IAccountCreditWriteAdapter — remote adapter was wired without outbox/saga strategy; transaction-boundary risk remains"
else
    check_pass "AwardRepository does NOT reference IAccountCreditWriteAdapter (wiring correctly deferred)"
fi

# --- Check 2: Both writes remain inside transactionTemplate in saveGiveOutPrizesAggregate ---
# Extract the transactionTemplate block inside saveGiveOutPrizesAggregate and verify both calls appear.
TX_BLOCK="$(awk '
    /void saveGiveOutPrizesAggregate/ { in_method = 1 }
    in_method && /transactionTemplate\.execute\(status -> \{/ { capture = 1 }
    capture { print }
    capture && /^            \}\);/ { exit }
' "$AWARD_REPO")"

if [[ "$TX_BLOCK" == *"transactionTemplate.execute"* ]] \
    && [[ "$TX_BLOCK" == *"updateOrCreateCreditAccount"* ]] \
    && [[ "$TX_BLOCK" == *"updateAwardRecordCompletedState"* ]]; then
    check_pass "Credit-account write (updateOrCreateCreditAccount) and award-record write (updateAwardRecordCompletedState) are both inside transactionTemplate.execute() in saveGiveOutPrizesAggregate"
else
    check_fail "Could not prove both writes are inside transactionTemplate in saveGiveOutPrizesAggregate — transaction boundary may have changed"
fi

# --- Check 3: Design doc contains B5 outbox strategy section ---
if grep -q "Phase 2.2-B5" "$DESIGN_DOC" && grep -q "credit_award_task" "$DESIGN_DOC"; then
    check_pass "Design doc contains Phase 2.2-B5 outbox strategy section (Phase 2.2-B5 heading and credit_award_task both present)"
else
    check_fail "Design doc is missing Phase 2.2-B5 outbox strategy section — update docs/archive/phases.md"
fi

# --- Check 4: Design doc explicitly forbids direct adapter wiring before outbox/saga ---
if grep -q "explicitly forbidden\|NOT be made until\|forbid\|explicitly prohibit" "$DESIGN_DOC"; then
    check_pass "Design doc contains explicit prohibition on direct remote adapter wiring before outbox/saga is implemented"
else
    check_fail "Design doc does not explicitly forbid direct adapter wiring — add a clear prohibition statement to the B5 section"
fi

# --- Check 5: Proposed DDL file exists ---
if [[ -f "$DDL_FILE" ]]; then
    check_pass "Proposed DDL file found: $DDL_FILE"
else
    check_fail "Proposed DDL file not found: $DDL_FILE — run Phase 2.2-B5 scaffold step"
fi

# --- Check 6: DDL contains UNIQUE constraint on award_order_id ---
if [[ -f "$DDL_FILE" ]] && grep -qE "UNIQUE KEY .*\\(.*award_order_id" "$DDL_FILE"; then
    check_pass "DDL contains UNIQUE constraint on award_order_id (idempotency key present)"
else
    check_fail "DDL does NOT contain UNIQUE constraint on award_order_id — idempotency key is missing; double-credit risk on retry"
fi

# --- Check 7: credit_award_task references are confined to outbox scaffold classes only ---
# Phase 2.2-B6 added ICreditAwardTaskDao, CreditAwardTask PO, AwardRepository (flag-guarded),
# and DispatchCreditAwardTaskJob (ConditionalOnProperty-guarded). These are expected.
# Any other reference is unexpected.
UNEXPECTED_REFS="$(grep -r "credit_award_task" \
    --include="*.java" \
    "$REPO_ROOT" \
    -l 2>/dev/null \
    | grep "/src/main/java/" \
    | grep -v "ICreditAwardTaskDao\|CreditAwardTask\.java\|AwardRepository\|DispatchCreditAwardTaskJob\|DynamicTableNamePlugin" \
    || true)"

if [[ -z "$UNEXPECTED_REFS" ]]; then
    check_pass "credit_award_task is only referenced inside expected outbox scaffold classes (B6 or later); no unexpected callers present"
else
    check_fail "Unexpected production Java source references credit_award_task outside outbox scaffold — review: $UNEXPECTED_REFS"
fi

# --- Check 8: AwardRepository still uses userCreditAccountDao (direct write path present) ---
if grep -q "userCreditAccountDao" "$AWARD_REPO"; then
    check_pass "AwardRepository still uses userCreditAccountDao (direct credit write path is active and unchanged)"
else
    check_fail "AwardRepository no longer references userCreditAccountDao — direct credit write may have been removed without outbox replacement in place"
fi

echo ""
echo "=== Summary ==="
echo "PASS: $PASS  FAIL: $FAIL"
echo ""
echo "Known pending work (not a test failure):"
echo "  - Outbox producer: insert credit_award_task row inside saveGiveOutPrizesAggregate transaction (replaces updateOrCreateCreditAccount)"
echo "  - Outbox consumer: XXL-Job poller scanning pending credit_award_task rows → IAccountCreditWriteAdapter.createOrder()"
echo "  - Deploy credit_award_task table (docs/sql/proposed-credit-award-task-outbox.sql) to staging before production"
echo "  - Validate poller idempotency end-to-end against replay scenarios before enabling"
echo "  - RaffleActivityPartakeService quota decrement: deferred, high risk"
echo "  - MQ idempotency and business-flow validation still required before enabling write flags"
echo ""
echo "Also run: ./scripts/validate-award-credit-path.sh (B4 checks — still required)"
echo ""

if [[ "$FAIL" -gt 0 ]]; then
    echo "RESULT: FAIL — $FAIL check(s) failed. Review output above."
    exit 1
else
    echo "RESULT: PASS — all $PASS checks passed."
    exit 0
fi

#!/usr/bin/env bash
# Phase 2.2-B6: Static audit of award credit outbox scaffold.
#
# Checks:
#   1.  Default flag is false in message-job-service application.yml.
#   2.  AwardRepository contains the flag field (awardCreditOutboxEnabled injected).
#   3.  AwardRepository flag=false branch: updateOrCreateCreditAccount still present inside an else block.
#   4.  AwardRepository flag=true branch: creditAwardTaskDao.insert present (outbox producer).
#   5.  AwardRepository flag=true branch: does NOT call updateOrCreateCreditAccount (direct write removed).
#   6.  AwardRepository still does NOT directly reference IAccountCreditWriteAdapter (adapter is consumer-only).
#   7.  DispatchCreditAwardTaskJob exists and is annotated @ConditionalOnProperty(account.award-credit-outbox.enabled).
#   8.  DispatchCreditAwardTaskJob references IAccountCreditWriteAdapter (consumer wiring correct).
#   9.  ICreditAwardTaskDao exists (outbox DAO scaffold present).
#   10. CreditAwardTask PO exists (outbox PO scaffold present).
#   11. credit_award_task_mapper.xml exists in big-market-app, message-job-service, and account-service.
#   12. DDL file still has UNIQUE constraint on award_order_id (idempotency key present).
#   13. No default startup path requires credit_award_task tables (flag defaults to false).
#   14. ICreditAwardTaskDao is annotated @DBRouterStrategy(splitTable = true).
#   15. DynamicTableNamePlugin knows how to rewrite credit_award_task to physical shard tables.
#   16. DispatchCreditAwardTaskJob scans all four table shards per DB.
#   17. docker-compose.yml exposes ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED to message-job-service container.
#
# None of these checks require a running stack.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
AWARD_REPO="$REPO_ROOT/big-market-infrastructure/src/main/java/com/dyx/market/infrastructure/adapter/repository/AwardRepository.java"
JOB_FILE="$REPO_ROOT/big-market-message-job-service/src/main/java/com/dyx/market/message/job/config/DispatchCreditAwardTaskJob.java"
DAO_FILE="$REPO_ROOT/big-market-infrastructure/src/main/java/com/dyx/market/infrastructure/dao/ICreditAwardTaskDao.java"
PO_FILE="$REPO_ROOT/big-market-infrastructure/src/main/java/com/dyx/market/infrastructure/dao/po/CreditAwardTask.java"
DDL_FILE="$REPO_ROOT/docs/sql/proposed-credit-award-task-outbox.sql"
TABLE_PLUGIN="$REPO_ROOT/big-market-starter-db-router/src/main/java/com/dyx/market/middleware/db/router/plugin/DynamicTableNamePlugin.java"
MJS_YML="$REPO_ROOT/big-market-message-job-service/src/main/resources/application.yml"
MAPPER_APP="$REPO_ROOT/big-market-app/src/main/resources/mybatis/mapper/mysql/credit_award_task_mapper.xml"
MAPPER_MJS="$REPO_ROOT/big-market-message-job-service/src/main/resources/mybatis/mapper/mysql/credit_award_task_mapper.xml"
MAPPER_ACC="$REPO_ROOT/big-market-account-service/src/main/resources/mybatis/mapper/mysql/credit_award_task_mapper.xml"
DOCKER_COMPOSE="$REPO_ROOT/docker-compose.yml"

PASS=0
FAIL=0

check_pass() { echo "[PASS] $1"; PASS=$((PASS + 1)); }
check_fail() { echo "[FAIL] $1"; FAIL=$((FAIL + 1)); }

echo "=== Phase 2.2-B6 Award Credit Outbox Scaffold Static Audit ==="
echo ""

# --- Check 1: Default flag is false in message-job-service config ---
if grep -q "ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED:false" "$MJS_YML"; then
    check_pass "Default flag account.award-credit-outbox.enabled=false in message-job-service application.yml (env default is false)"
else
    check_fail "Default flag not found or not false in message-job-service application.yml — runtime behavior may be altered"
fi

# --- Check 2: AwardRepository has the flag field injected ---
if grep -q "awardCreditOutboxEnabled" "$AWARD_REPO"; then
    check_pass "AwardRepository declares awardCreditOutboxEnabled flag field (@Value injected)"
else
    check_fail "AwardRepository is missing awardCreditOutboxEnabled — flag-guarded branch not wired"
fi

# --- Check 3: flag=false path still has updateOrCreateCreditAccount in else branch ---
# Extract the else block (flag=false branch)
ELSE_BLOCK="$(awk '/} else \{/,/^            \}$/' "$AWARD_REPO" | head -30)"
if echo "$ELSE_BLOCK" | grep -q "updateOrCreateCreditAccount"; then
    check_pass "AwardRepository flag=false branch still calls updateOrCreateCreditAccount (direct local write unchanged)"
else
    check_fail "AwardRepository flag=false branch does NOT call updateOrCreateCreditAccount — default behavior was altered"
fi

# --- Check 4: flag=true path calls creditAwardTaskDao.insert (outbox producer present) ---
if grep -q "creditAwardTaskDao.insert" "$AWARD_REPO"; then
    check_pass "AwardRepository flag=true branch calls creditAwardTaskDao.insert (outbox producer is wired)"
else
    check_fail "AwardRepository flag=true branch does NOT call creditAwardTaskDao.insert — outbox producer is missing"
fi

# --- Check 5: flag=true path does NOT call updateOrCreateCreditAccount ---
# Extract the if (awardCreditOutboxEnabled) block
IF_OUTBOX_BLOCK="$(awk '/if \(awardCreditOutboxEnabled\)/,/^            } else \{/' "$AWARD_REPO" | head -40)"
if echo "$IF_OUTBOX_BLOCK" | grep -q "updateOrCreateCreditAccount"; then
    check_fail "AwardRepository flag=true branch calls updateOrCreateCreditAccount — direct local write was NOT replaced by outbox"
else
    check_pass "AwardRepository flag=true branch does NOT call updateOrCreateCreditAccount (direct write correctly replaced by outbox)"
fi

# --- Check 6: AwardRepository does NOT reference IAccountCreditWriteAdapter directly ---
if grep -q "IAccountCreditWriteAdapter\|accountCreditWriteAdapter" "$AWARD_REPO"; then
    check_fail "AwardRepository references IAccountCreditWriteAdapter — adapter must only be used by the outbox consumer (DispatchCreditAwardTaskJob), not the producer"
else
    check_pass "AwardRepository does NOT reference IAccountCreditWriteAdapter (adapter correctly kept in consumer only)"
fi

# --- Check 7: DispatchCreditAwardTaskJob exists and is @ConditionalOnProperty guarded ---
if [[ -f "$JOB_FILE" ]] && grep -q "ConditionalOnProperty" "$JOB_FILE" && grep -q "award-credit-outbox" "$JOB_FILE"; then
    check_pass "DispatchCreditAwardTaskJob exists and is @ConditionalOnProperty(account.award-credit-outbox.enabled) guarded"
else
    check_fail "DispatchCreditAwardTaskJob missing or not properly @ConditionalOnProperty guarded — consumer may activate without the flag"
fi

# --- Check 8: DispatchCreditAwardTaskJob references IAccountCreditWriteAdapter ---
if [[ -f "$JOB_FILE" ]] && grep -q "IAccountCreditWriteAdapter\|accountCreditWriteAdapter" "$JOB_FILE"; then
    check_pass "DispatchCreditAwardTaskJob references IAccountCreditWriteAdapter (consumer dispatch wiring present)"
else
    check_fail "DispatchCreditAwardTaskJob does NOT reference IAccountCreditWriteAdapter — credit dispatch is not wired in consumer"
fi

# --- Check 9: ICreditAwardTaskDao exists ---
if [[ -f "$DAO_FILE" ]]; then
    check_pass "ICreditAwardTaskDao exists (outbox DAO scaffold present)"
else
    check_fail "ICreditAwardTaskDao not found — outbox DAO scaffold is missing"
fi

# --- Check 10: CreditAwardTask PO exists ---
if [[ -f "$PO_FILE" ]]; then
    check_pass "CreditAwardTask PO exists (outbox PO scaffold present)"
else
    check_fail "CreditAwardTask PO not found — outbox PO scaffold is missing"
fi

# --- Check 11: Mapper XML exists in all three services ---
MAPPER_MISSING=0
for f in "$MAPPER_APP" "$MAPPER_MJS" "$MAPPER_ACC"; do
    if [[ ! -f "$f" ]]; then
        check_fail "credit_award_task_mapper.xml missing: $f"
        MAPPER_MISSING=$((MAPPER_MISSING + 1))
    fi
done
if [[ "$MAPPER_MISSING" -eq 0 ]]; then
    check_pass "credit_award_task_mapper.xml present in big-market-app, message-job-service, and account-service"
fi

# --- Check 12: DDL still has UNIQUE constraint on award_order_id ---
if [[ -f "$DDL_FILE" ]] && grep -qE "UNIQUE KEY .*award_order_id" "$DDL_FILE"; then
    check_pass "DDL contains UNIQUE constraint on award_order_id (idempotency key preserved)"
else
    check_fail "DDL does NOT contain UNIQUE constraint on award_order_id — idempotency key is missing; double-credit risk on retry"
fi

# --- Check 13: No default startup path requires credit_award_task tables ---
# The flag defaults to false; verify no unconditional reference to credit_award_task outside the flag-guarded code.
# We accept that ICreditAwardTaskDao, CreditAwardTask, and DispatchCreditAwardTaskJob reference the table name —
# these are fine because DAO/PO injection with Spring's lazy=false won't execute SQL unless called.
# The critical check is that no @PostConstruct, startup runner, or non-conditional bean calls queryPendingTasks/insert.
UNCONDITIONAL_REFS="$(grep -r "creditAwardTaskDao\.\|queryPendingTasks\|credit_award_task" \
    --include="*.java" \
    "$REPO_ROOT" \
    -l 2>/dev/null \
    | grep "/src/main/java/" \
    | grep -v "ICreditAwardTaskDao\|CreditAwardTask\.java\|AwardRepository\|DispatchCreditAwardTaskJob\|DynamicTableNamePlugin" \
    || true)"

if [[ -z "$UNCONDITIONAL_REFS" ]]; then
    check_pass "credit_award_task is only referenced in expected scaffold classes (DAO, PO, AwardRepository, DispatchCreditAwardTaskJob) — no unexpected callers"
else
    check_fail "Unexpected Java sources reference credit_award_task outside the scaffold: $UNCONDITIONAL_REFS"
fi

# --- Check 14: DAO is marked as split-table mapper ---
if [[ -f "$DAO_FILE" ]] && grep -q "DBRouterStrategy(splitTable = true)" "$DAO_FILE"; then
    check_pass "ICreditAwardTaskDao is annotated @DBRouterStrategy(splitTable = true)"
else
    check_fail "ICreditAwardTaskDao is missing @DBRouterStrategy(splitTable = true) — logical table credit_award_task would not route to physical shards"
fi

# --- Check 15: Dynamic table-name plugin rewrites credit_award_task ---
if [[ -f "$TABLE_PLUGIN" ]] && grep -q '"credit_award_task"' "$TABLE_PLUGIN"; then
    check_pass "DynamicTableNamePlugin includes credit_award_task in sharded table whitelist"
else
    check_fail "DynamicTableNamePlugin does not include credit_award_task — SQL would target the logical table name"
fi

# --- Check 16: Poller scans all four table shards in each DB ---
if [[ -f "$JOB_FILE" ]] && grep -q "tbIdx < 4" "$JOB_FILE" && grep -q "dbRouter.setTBKey(tbIdx)" "$JOB_FILE"; then
    check_pass "DispatchCreditAwardTaskJob iterates all four table shards per DB"
else
    check_fail "DispatchCreditAwardTaskJob does not clearly iterate all four table shards per DB — pending tasks may be missed"
fi

# --- Check 17: docker-compose.yml exposes ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED to message-job-service ---
if [[ -f "$DOCKER_COMPOSE" ]] && grep -A 30 "big-market-message-job-service:" "$DOCKER_COMPOSE" \
    | grep -q "ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED"; then
    check_pass "docker-compose.yml exposes ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED to message-job-service container"
else
    check_fail "docker-compose.yml does NOT expose ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED — container flag cannot be overridden at runtime"
fi

echo ""
echo "=== Summary ==="
echo "PASS: $PASS  FAIL: $FAIL"
echo ""
echo "Runtime behavior (flag=false, default):"
echo "  - AwardRepository.saveGiveOutPrizesAggregate: Redis lock → dbRouter → transactionTemplate → updateOrCreateCreditAccount + updateAwardRecordCompletedState (UNCHANGED)"
echo "  - No credit_award_task table access (tables do not need to exist)"
echo "  - DispatchCreditAwardTaskJob bean is NOT instantiated (ConditionalOnProperty=false)"
echo ""
echo "Scaffold behavior (flag=true, requires SQL applied first):"
echo "  - AwardRepository: inserts credit_award_task row inside transactionTemplate; updateOrCreateCreditAccount NOT called"
echo "  - DispatchCreditAwardTaskJob polls pending rows → IAccountCreditWriteAdapter.createOrder(awardOrderId as outBusinessNo)"
echo "  - Duplicate dispatch guarded by outBusinessNo idempotency in account-service"
echo ""
echo "Before enabling flag=true:"
echo "  1. Apply docs/sql/proposed-credit-award-task-outbox.sql to big_market_01 and big_market_02"
echo "  2. Verify tables credit_award_task_000..003 exist in both DBs"
echo "  3. Run integration validation (outbox flag=true) before staging promotion"
echo ""
echo "Also run: ./scripts/validate-award-credit-path.sh (B4) and ./scripts/validate-award-credit-outbox-readiness.sh (B5)"
echo ""

if [[ "$FAIL" -gt 0 ]]; then
    echo "RESULT: FAIL — $FAIL check(s) failed. Review output above."
    exit 1
else
    echo "RESULT: PASS — all $PASS checks passed."
    exit 0
fi

#!/usr/bin/env bash
# Mapper / DDL consistency gates (NR-003/005/011).
set -u

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PASS=0
FAIL=0

pass() { echo "[PASS] $*"; PASS=$((PASS + 1)); }
fail() { echo "[FAIL] $*"; FAIL=$((FAIL + 1)); }

echo "=== Mapper / DDL gates ==="

MARKET_MAPPER="$REPO_ROOT/big-market-market-service/src/main/resources/mybatis/mapper/mysql/raffle_activity_account_mapper.xml"
JOB_MAPPER="$REPO_ROOT/big-market-message-job-service/src/main/resources/mybatis/mapper/mysql/raffle_activity_account_mapper.xml"
ACCOUNT_MAPPER="$REPO_ROOT/big-market-account-service/src/main/resources/mybatis/mapper/mysql/raffle_activity_account_mapper.xml"

for stmt in addAccountTotalSurplusQuota addAccountMonthSurplusQuota addAccountDaySurplusQuota; do
  for label_file in "market:$MARKET_MAPPER" "message-job:$JOB_MAPPER" "account:$ACCOUNT_MAPPER"; do
    label="${label_file%%:*}"
    file="${label_file#*:}"
    if [[ -f "$file" ]] && grep -q "id=\"$stmt\"" "$file"; then
      pass "$label mapper has $stmt"
    else
      fail "$label mapper missing $stmt in $file"
    fi
  done
done

if grep -q 'uq_user_id' "$REPO_ROOT/docs/dev-ops/mysql/sql/big_market_02.sql"; then
  pass "db02 user_credit_account has uq_user_id"
else
  fail "db02 user_credit_account missing uq_user_id"
fi

if grep -q 'uq_user_request' "$REPO_ROOT/docs/dev-ops/mysql/sql/z-reconcile-tables.sql"; then
  pass "chat_credit_session unique (user_id, request_id)"
else
  fail "chat_credit_session missing composite unique key"
fi

echo ""
echo "Gates: $PASS passed, $FAIL failed"
[[ $FAIL -eq 0 ]]

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

if grep -q 'strategy_award_stock_decrement_ledger' "$REPO_ROOT/docs/dev-ops/mysql/sql/z-reconcile-tables.sql" \
  && grep -q 'uq_reservation_id' "$REPO_ROOT/docs/dev-ops/mysql/sql/z-reconcile-tables.sql"; then
  pass "strategy_award_stock_decrement_ledger unique reservation_id"
else
  fail "strategy stock decrement ledger missing or without unique key"
fi

if grep -q 'activity_sku_stock_decrement_ledger' "$REPO_ROOT/docs/dev-ops/mysql/sql/z-reconcile-tables.sql" \
  && grep -q 'uq_sku_lock_surplus' "$REPO_ROOT/docs/dev-ops/mysql/sql/z-reconcile-tables.sql"; then
  pass "activity_sku_stock_decrement_ledger unique (sku, lock_surplus)"
else
  fail "sku stock decrement ledger missing or without unique key"
fi

for svc in market-service message-job-service; do
  for mapper in strategy_award_stock_decrement_ledger_mapper.xml activity_sku_stock_decrement_ledger_mapper.xml; do
    file="$REPO_ROOT/big-market-$svc/src/main/resources/mybatis/mapper/mysql/$mapper"
    if [[ -f "$file" ]]; then
      pass "$svc has $mapper"
    else
      fail "$svc missing $mapper"
    fi
  done
done

echo ""
echo "── Mapper statement-id drift (market vs message-job) ──"
if REPO_ROOT="$REPO_ROOT" python3 - <<'PY'
import os, re, sys

root = os.environ["REPO_ROOT"]
dirs = [
    os.path.join(root, "big-market-market-service/src/main/resources/mybatis/mapper/mysql"),
    os.path.join(root, "big-market-message-job-service/src/main/resources/mybatis/mapper/mysql"),
]

def statements(path):
    text = open(path, encoding="utf-8").read()
    ns_m = re.search(r'namespace="([^"]+)"', text)
    ns = ns_m.group(1) if ns_m else path
    out = {}
    for m in re.finditer(
        r'<(select|insert|update|delete)\s+[^>]*id="([^"]+)"[^>]*>(.*?)</\1>',
        text, re.S | re.I):
        sid = m.group(2)
        body = re.sub(r'\s+', ' ', m.group(3)).strip()
        out[ns + "#" + sid] = body
    return out

by_file = {}
for d in dirs:
    if not os.path.isdir(d):
        continue
    for name in sorted(os.listdir(d)):
        if not name.endswith(".xml"):
            continue
        by_file.setdefault(name, {})[d] = statements(os.path.join(d, name))

fail = 0
checked = 0
for name, copies in sorted(by_file.items()):
    if len(copies) < 2:
        continue
    keys = set()
    for stmts in copies.values():
        keys |= set(stmts)
    for key in sorted(keys):
        bodies = [stmts[key] for stmts in copies.values() if key in stmts]
        if len(bodies) < 2:
            continue
        checked += 1
        if bodies[0] != bodies[1]:
            print("DRIFT " + name + " " + key)
            fail += 1

print("compared=%d drift=%d" % (checked, fail))
sys.exit(1 if fail else 0)
PY
then
  pass "market/message-job mapper statement bodies aligned for shared ids"
else
  fail "market/message-job mapper statement-id drift detected"
fi

echo ""
echo "Gates: $PASS passed, $FAIL failed"
[[ $FAIL -eq 0 ]]

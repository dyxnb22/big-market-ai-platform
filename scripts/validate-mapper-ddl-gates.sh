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

if grep -q 'raffle_quota_decrement_ledger_000' "$REPO_ROOT/docs/dev-ops/mysql/sql/z-reconcile-tables.sql" \
  && grep -q 'raffle_quota_decrement_ledger_003' "$REPO_ROOT/docs/dev-ops/mysql/sql/z-reconcile-tables.sql" \
  && grep -q 'uq_user_activity_biz' "$REPO_ROOT/docs/dev-ops/mysql/sql/z-reconcile-tables.sql"; then
  pass "quota decrement ledger shards and idempotency key declared in Docker DDL"
else
  fail "quota decrement ledger Docker DDL is incomplete"
fi

for schema in big_market big_market_01 big_market_02; do
  if [[ "$schema" = "big_market_02" ]]; then
    if grep -q 'CREATE TABLE IF NOT EXISTS `pending_remote_write_task` LIKE `big_market_01`.`pending_remote_write_task`' \
      "$REPO_ROOT/docs/dev-ops/mysql/sql/z-reconcile-tables.sql" \
      && awk '
        $0 ~ "USE `big_market_01`;" { in_schema=1; next }
        /^USE `/ && $0 !~ "USE `big_market_01`;" { in_schema=0 }
        in_schema && /`state`[[:space:]]+VARCHAR\(24\)/ { found=1 }
        END { exit !found }
      ' "$REPO_ROOT/docs/dev-ops/mysql/sql/z-reconcile-tables.sql"; then
      pass "$schema.pending_remote_write_task inherits VARCHAR(24) state"
    else
      fail "$schema.pending_remote_write_task must inherit VARCHAR(24) state"
    fi
    continue
  fi

  if awk -v schema="$schema" '
    $0 ~ "USE `" schema "`;" { in_schema=1; next }
    /^USE `/ && $0 !~ "USE `" schema "`;" { in_schema=0 }
    in_schema && /CREATE TABLE IF NOT EXISTS `pending_remote_write_task`/ { found=1 }
    in_schema && /`state`[[:space:]]+VARCHAR\(24\)/ { state_ok=1 }
    END { exit !(found && state_ok) }
  ' "$REPO_ROOT/docs/dev-ops/mysql/sql/z-reconcile-tables.sql"; then
    pass "$schema.pending_remote_write_task.state is VARCHAR(24)"
  else
    fail "$schema.pending_remote_write_task.state must be VARCHAR(24)"
  fi
done

QUOTA_LEDGER_MAPPER="$REPO_ROOT/big-market-account-service/src/main/resources/mybatis/mapper/mysql/raffle_quota_decrement_ledger_mapper.xml"
if [[ -f "$QUOTA_LEDGER_MAPPER" ]] && grep -q 'raffle_quota_decrement_ledger' "$QUOTA_LEDGER_MAPPER"; then
  pass "account mapper uses quota decrement ledger covered by Docker DDL"
else
  fail "account quota decrement ledger mapper missing or uncovered"
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
echo "── Mapper statement/result contract drift (explicit allowlist) ──"
if REPO_ROOT="$REPO_ROOT" python3 - <<'PY'
import os, re, sys

root = os.environ["REPO_ROOT"]
dirs = [
    ("market-service", os.path.join(root, "big-market-market-service/src/main/resources/mybatis/mapper/mysql")),
    ("message-job-service", os.path.join(root, "big-market-message-job-service/src/main/resources/mybatis/mapper/mysql")),
    ("account-service", os.path.join(root, "big-market-account-service/src/main/resources/mybatis/mapper/mysql")),
    ("chatbot-service", os.path.join(root, "big-market-chatbot-service/src/main/resources/mybatis/mapper/mysql")),
]

allowlist_path = os.path.join(root, "docs/mapper-statement-allowlist.txt")
allowlist = {}
with open(allowlist_path, encoding="utf-8") as stream:
    for line_number, raw in enumerate(stream, 1):
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        parts = line.split("|", 4)
        if len(parts) != 5:
            raise SystemExit("invalid allowlist row %d: %s" % (line_number, line))
        mapper, key, kind, services, reason = parts
        allowlist[(mapper, key)] = {
            "kind": kind,
            "services": set(services.split(",")),
            "reason": reason,
        }

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
for service, d in dirs:
    if not os.path.isdir(d):
        continue
    for name in sorted(os.listdir(d)):
        if not name.endswith(".xml"):
            continue
        by_file.setdefault(name, {})[service] = statements(os.path.join(d, name))

errors = []
checked = 0
allowlist_hits = set()
for name, copies in sorted(by_file.items()):
    if len(copies) < 2:
        continue
    keys = set()
    for stmts in copies.values():
        keys |= set(stmts)
    for key in sorted(keys):
        checked += 1
        present = {service for service, stmts in copies.items() if key in stmts}
        entry = allowlist.get((name, key))
        if present != set(copies):
            if not entry or entry["kind"] != "SERVICE_SPECIFIC" or present != entry["services"]:
                errors.append("MISSING/EXTRA %s %s present=%s" % (name, key, ",".join(sorted(present))))
            else:
                allowlist_hits.add((name, key))
            continue
        bodies = {stmts[key] for stmts in copies.values()}
        if len(bodies) > 1:
            if not entry or entry["kind"] != "DRIFT":
                errors.append("DRIFT %s %s" % (name, key))
            else:
                allowlist_hits.add((name, key))

for key, entry in allowlist.items():
    if key not in allowlist_hits and entry["kind"] in ("DRIFT", "SERVICE_SPECIFIC"):
        # SERVICE_SPECIFIC entries may belong to a mapper present in only one
        # launcher, so verify them against all service files below instead.
        mapper, statement = key
        observed = set()
        for service, directory in dirs:
            path = os.path.join(directory, mapper)
            if os.path.isfile(path) and statement in statements(path):
                observed.add(service)
        if observed != entry["services"]:
            errors.append("STALE ALLOWLIST %s %s observed=%s" % (mapper, statement, ",".join(sorted(observed))))

print("compared=%d exceptions=%d errors=%d" % (checked, len(allowlist_hits), len(errors)))
for error in errors:
    print(error)
sys.exit(1 if errors else 0)
PY
then
  pass "all duplicated mapper statement contracts aligned or explicitly allowlisted"
else
  fail "mapper statement/result contract drift or missing allowlist detected"
fi

echo ""
echo "Gates: $PASS passed, $FAIL failed"
[[ $FAIL -eq 0 ]]

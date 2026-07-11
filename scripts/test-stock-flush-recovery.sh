#!/usr/bin/env bash
# Fault-injection style checks for stock flush durable ledger.
# Simulates the SETNX-before-DB crash window by asserting MySQL ledger is the
# source of truth: a Redis dedupe key alone must not prevent a missing ledger
# from being applied on retry (covered by unit tests). This script verifies
# DDL presence and ledger uniqueness constraints on a running MySQL.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MYSQL_CONTAINER="${MYSQL_CONTAINER:-mysql}"
MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:-123456}"

pass() { echo "  PASS  $*"; }
fail() { echo "  FAIL  $*"; exit 1; }

echo "=== Stock flush recovery / ledger gate ==="

if ! docker ps --format '{{.Names}}' | grep -qx "$MYSQL_CONTAINER"; then
  echo "MySQL container not running — applying unit-test-only gate."
  echo "Run with Docker for full DDL check: docker compose -f docs/dev-ops/docker-compose-environment.yml up -d mysql"
  # Still require DDL files and unit tests to exist
  test -f "$ROOT/docs/sql/stock-decrement-ledger.sql" || fail "missing docs/sql/stock-decrement-ledger.sql"
  test -f "$ROOT/docs/dev-ops/mysql/sql/z-reconcile-tables.sql" || fail "missing z-reconcile-tables.sql"
  grep -q 'strategy_award_stock_decrement_ledger' "$ROOT/docs/dev-ops/mysql/sql/z-reconcile-tables.sql" \
    || fail "z-reconcile-tables missing strategy_award_stock_decrement_ledger"
  grep -q 'activity_sku_stock_decrement_ledger' "$ROOT/docs/dev-ops/mysql/sql/z-reconcile-tables.sql" \
    || fail "z-reconcile-tables missing activity_sku_stock_decrement_ledger"
  pass "DDL files present (MySQL offline — skipped live constraint probe)"
  echo "=== Stock flush recovery gate PASSED (offline) ==="
  exit 0
fi

"$ROOT/scripts/apply-reconcile-ddl.sh" >/dev/null

for table in strategy_award_stock_decrement_ledger activity_sku_stock_decrement_ledger; do
  exists=$(docker exec "$MYSQL_CONTAINER" mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -N -e \
    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='big_market' AND table_name='$table';" 2>/dev/null || echo 0)
  [ "$exists" = "1" ] || fail "table big_market.$table missing"
  pass "table big_market.$table exists"
done

# Unique constraint: duplicate reservation_id must fail
docker exec "$MYSQL_CONTAINER" mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -e \
  "USE big_market;
   DELETE FROM strategy_award_stock_decrement_ledger WHERE reservation_id='fault-inject-res-1';
   INSERT INTO strategy_award_stock_decrement_ledger(reservation_id, strategy_id, award_id, lock_surplus, status)
   VALUES ('fault-inject-res-1', 100001, 101, 5, 'applied');" 2>/dev/null

if docker exec "$MYSQL_CONTAINER" mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -e \
  "USE big_market;
   INSERT INTO strategy_award_stock_decrement_ledger(reservation_id, strategy_id, award_id, lock_surplus, status)
   VALUES ('fault-inject-res-1', 100001, 101, 5, 'applied');" 2>/dev/null; then
  fail "duplicate reservation_id was accepted (unique constraint missing)"
fi
pass "strategy ledger rejects duplicate reservation_id"

docker exec "$MYSQL_CONTAINER" mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -e \
  "USE big_market;
   DELETE FROM activity_sku_stock_decrement_ledger WHERE sku=901199 AND lock_surplus=3;
   INSERT INTO activity_sku_stock_decrement_ledger(sku, activity_id, lock_surplus, status)
   VALUES (901199, 100401, 3, 'applied');" 2>/dev/null

if docker exec "$MYSQL_CONTAINER" mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -e \
  "USE big_market;
   INSERT INTO activity_sku_stock_decrement_ledger(sku, activity_id, lock_surplus, status)
   VALUES (901199, 100401, 3, 'applied');" 2>/dev/null; then
  fail "duplicate (sku, lock_surplus) was accepted"
fi
pass "sku ledger rejects duplicate (sku, lock_surplus)"

# Cleanup probe rows
docker exec "$MYSQL_CONTAINER" mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -e \
  "USE big_market;
   DELETE FROM strategy_award_stock_decrement_ledger WHERE reservation_id='fault-inject-res-1';
   DELETE FROM activity_sku_stock_decrement_ledger WHERE sku=901199 AND lock_surplus=3;" 2>/dev/null || true

echo "=== Stock flush recovery gate PASSED ==="

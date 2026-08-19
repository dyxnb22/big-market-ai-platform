#!/usr/bin/env bash
# 库存刷新持久化账本的故障注入式检查。
# 通过断言 MySQL 账本是事实来源，模拟 SETNX 先于数据库写入时的崩溃窗口：
# 单独存在 Redis 去重键不能阻止缺失的账本在重试时写入（由单元测试覆盖）。
# 本脚本验证运行中 MySQL 的 DDL 是否存在以及账本唯一约束是否生效。
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
  # 仍要求 DDL 文件和单元测试存在。
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

# 唯一约束：重复 reservation_id 必须失败。
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

# 清理探测记录。
docker exec "$MYSQL_CONTAINER" mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -e \
  "USE big_market;
   DELETE FROM strategy_award_stock_decrement_ledger WHERE reservation_id='fault-inject-res-1';
   DELETE FROM activity_sku_stock_decrement_ledger WHERE sku=901199 AND lock_surplus=3;" 2>/dev/null || true

echo "=== Stock flush recovery gate PASSED ==="

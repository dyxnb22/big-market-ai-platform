#!/usr/bin/env bash
# 将对账/聊天会话 DDL 应用到现有 Docker MySQL 卷。
# 新建卷初始化时已经执行 docs/dev-ops/mysql/sql/z-reconcile-tables.sql；
# 本脚本用于该文件出现以前创建的开发栈，并且可以幂等执行。
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SQL_FILE="$ROOT/docs/dev-ops/mysql/sql/z-reconcile-tables.sql"
MYSQL_CONTAINER="${MYSQL_CONTAINER:-mysql}"
MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:-123456}"

if [ ! -f "$SQL_FILE" ]; then
  echo "Missing reconcile DDL: $SQL_FILE" >&2
  exit 1
fi

if ! docker ps --format '{{.Names}}' | grep -qx "$MYSQL_CONTAINER"; then
  echo "MySQL container '$MYSQL_CONTAINER' is not running." >&2
  echo "Start infra first: docker compose -f docs/dev-ops/docker-compose-environment.yml up -d mysql" >&2
  exit 1
fi

echo "Applying reconcile DDL via container '$MYSQL_CONTAINER'..."
docker exec -i "$MYSQL_CONTAINER" mysql -uroot -p"$MYSQL_ROOT_PASSWORD" < "$SQL_FILE"

# continuation_pending 长度为 20 个字符。旧卷使用 VARCHAR(16)，
# 远程补偿成功后在 STRICT_TRANS_TABLES 模式下会因此失败。
for schema in big_market big_market_01 big_market_02; do
  state_length=$(docker exec "$MYSQL_CONTAINER" mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -N -e \
    "SELECT COALESCE(MAX(CHARACTER_MAXIMUM_LENGTH), 0)
     FROM information_schema.columns
     WHERE table_schema='$schema' AND table_name='pending_remote_write_task' AND column_name='state';" \
    2>/dev/null || echo 0)
  if [ "${state_length:-0}" -gt 0 ] && [ "${state_length:-0}" -lt 24 ]; then
    echo "Widening $schema.pending_remote_write_task.state to VARCHAR(24)..."
    docker exec "$MYSQL_CONTAINER" mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -e \
      "ALTER TABLE \`$schema\`.\`pending_remote_write_task\`
       MODIFY COLUMN \`state\` VARCHAR(24) NOT NULL DEFAULT 'pending';"
  fi
done

# 为旧版 chat_credit_session 表幂等迁移列。
for schema in big_market_01 big_market_02; do
  col=$(docker exec "$MYSQL_CONTAINER" mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -N -e \
    "SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema='$schema' AND table_name='chat_credit_session' AND column_name='deduct_state';" 2>/dev/null || echo 0)
  if [ "$col" = "0" ]; then
    echo "Adding deduct_state to $schema.chat_credit_session..."
    docker exec "$MYSQL_CONTAINER" mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -e \
      "ALTER TABLE \`$schema\`.\`chat_credit_session\`
       ADD COLUMN \`deduct_state\` VARCHAR(16) NOT NULL DEFAULT 'deducted' AFTER \`deduct_amount\`;" \
      || echo "WARN: could not add deduct_state on $schema (table may be missing)"
  fi
done

# 旧卷需要保留原始配额桶事实，以支持跨日/月回滚。
for schema in big_market_01 big_market_02; do
  for shard in 000 001 002 003; do
    for column in month day; do
      present=$(docker exec "$MYSQL_CONTAINER" mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -N -e \
        "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='$schema' AND table_name='raffle_quota_decrement_ledger_${shard}' AND column_name='$column';" 2>/dev/null || echo 0)
      if [ "${present:-0}" = "0" ]; then
        if [ "$column" = month ]; then definition="VARCHAR(7) NOT NULL DEFAULT ''"; else definition="VARCHAR(10) NOT NULL DEFAULT ''"; fi
        docker exec "$MYSQL_CONTAINER" mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -e \
          "ALTER TABLE \`$schema\`.\`raffle_quota_decrement_ledger_${shard}\` ADD COLUMN \`$column\` $definition;"
      fi
    done
  done
done

# 旧共享卷中的恢复账本可能缺少 state 列。
restore_status=$(docker exec "$MYSQL_CONTAINER" mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -N -e \
  "SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema='big_market' AND table_name='activity_sku_stock_restore_ledger' AND column_name='status';" 2>/dev/null || echo 0)
if [ "${restore_status:-0}" = "0" ]; then
  echo "Adding status to big_market.activity_sku_stock_restore_ledger..."
  docker exec "$MYSQL_CONTAINER" mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -e \
    "ALTER TABLE big_market.activity_sku_stock_restore_ledger
     ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'reserved' AFTER reservation_id;" \
    || echo "WARN: could not add status to activity_sku_stock_restore_ledger (table may be missing)"
fi

# 旧共享卷创建时尚未有 SKU 扣减账本上的积分支付预留关联。
# 对历史抽奖记录，该字段允许为空。
decrement_reservation=$(docker exec "$MYSQL_CONTAINER" mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -N -e \
  "SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema='big_market' AND table_name='activity_sku_stock_decrement_ledger' AND column_name='reservation_id';" 2>/dev/null || echo 0)
if [ "${decrement_reservation:-0}" = "0" ]; then
  echo "Adding reservation_id to big_market.activity_sku_stock_decrement_ledger..."
  docker exec "$MYSQL_CONTAINER" mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -e \
    "ALTER TABLE big_market.activity_sku_stock_decrement_ledger
     ADD COLUMN reservation_id VARCHAR(128) DEFAULT NULL AFTER lock_surplus,
     ADD KEY idx_reservation (reservation_id);" \
    || echo "WARN: could not add reservation_id to activity_sku_stock_decrement_ledger (table may be missing)"
fi

docker exec "$MYSQL_CONTAINER" mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -N -e \
  "SELECT table_schema, table_name FROM information_schema.tables
   WHERE (table_schema IN ('big_market_01','big_market_02') AND
          (table_name = 'chat_credit_session' OR table_name LIKE 'raffle_quota_decrement_ledger_%'))
      OR (table_schema = 'big_market' AND table_name IN (
            'strategy_award_stock_decrement_ledger',
            'activity_sku_stock_decrement_ledger',
            'activity_sku_stock_restore_ledger',
            'pending_remote_write_task'))
   ORDER BY table_schema, table_name;"

echo "Reconcile DDL applied (chat-credit session, stock and quota decrement ledgers, and related tables)."

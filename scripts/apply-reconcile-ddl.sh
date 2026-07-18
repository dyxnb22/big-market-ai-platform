#!/usr/bin/env bash
# Apply reconcile / chat-session DDL to an existing Docker MySQL volume.
# Fresh volumes already run docs/dev-ops/mysql/sql/z-reconcile-tables.sql on init;
# this script is idempotent for dev stacks created before that file existed.
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

# continuation_pending is 20 characters. Older volumes used VARCHAR(16),
# which fails under STRICT_TRANS_TABLES after a remote compensation succeeds.
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

# Idempotent column migrate for older chat_credit_session tables
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

# Older volumes need the original quota bucket facts for cross-day/month rollback.
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

# Older shared volumes may have the restore ledger without a state column.
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

# Older shared volumes predate the credit-pay reservation link on the SKU
# decrement ledger. It is nullable for legacy draw rows.
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

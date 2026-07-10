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

docker exec "$MYSQL_CONTAINER" mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -N -e \
  "SELECT table_schema, table_name FROM information_schema.tables
   WHERE (table_schema IN ('big_market_01','big_market_02') AND table_name = 'chat_credit_session')
      OR (table_schema = 'big_market' AND table_name IN (
            'strategy_award_stock_decrement_ledger',
            'activity_sku_stock_decrement_ledger'))
   ORDER BY table_schema, table_name;"

echo "Reconcile DDL applied (chat_credit_session, stock decrement ledgers, and related tables)."

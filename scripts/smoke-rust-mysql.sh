#!/usr/bin/env bash
# Smoke Rust API against MySQL backend (skips gracefully when MySQL is unavailable).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MYSQL_HOST="${MYSQL_HOST:-127.0.0.1}"
MYSQL_PORT="${MYSQL_PORT:-13306}"
MYSQL_USER="${MYSQL_USER:-root}"
MYSQL_PASS="${MYSQL_PASS:-123456}"
MYSQL_DB="${MYSQL_DB:-big_market}"
BM_MYSQL_URL="${BM_MYSQL_URL:-mysql://${MYSQL_USER}:${MYSQL_PASS}@${MYSQL_HOST}:${MYSQL_PORT}/${MYSQL_DB}}"

if ! command -v mysql >/dev/null 2>&1; then
  echo "SKIP  smoke-rust-mysql: mysql client not installed"
  exit 0
fi

if ! mysql -h"$MYSQL_HOST" -P"$MYSQL_PORT" -u"$MYSQL_USER" -p"$MYSQL_PASS" -e "SELECT 1" >/dev/null 2>&1; then
  echo "SKIP  smoke-rust-mysql: MySQL not reachable at ${MYSQL_HOST}:${MYSQL_PORT}"
  exit 0
fi

echo "=== Rust MySQL smoke (BM_BACKEND=mysql) ==="

ensure_ledger() {
  local schema="$1"
  mysql -h"$MYSQL_HOST" -P"$MYSQL_PORT" -u"$MYSQL_USER" -p"$MYSQL_PASS" "$MYSQL_DB" <<SQL
CREATE TABLE IF NOT EXISTS \`${schema}\`.\`raffle_quota_decrement_ledger_000\` (
    \`id\` BIGINT NOT NULL AUTO_INCREMENT,
    \`user_id\` VARCHAR(128) NOT NULL,
    \`activity_id\` BIGINT NOT NULL,
    \`out_business_no\` VARCHAR(64) NOT NULL,
    \`status\` VARCHAR(16) NOT NULL DEFAULT 'applied',
    \`create_time\` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    \`update_time\` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (\`id\`),
    UNIQUE KEY \`uq_user_activity_biz\` (\`user_id\`, \`activity_id\`, \`out_business_no\`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS \`${schema}\`.\`raffle_quota_decrement_ledger_001\`
  LIKE \`${schema}\`.\`raffle_quota_decrement_ledger_000\`;
CREATE TABLE IF NOT EXISTS \`${schema}\`.\`raffle_quota_decrement_ledger_002\`
  LIKE \`${schema}\`.\`raffle_quota_decrement_ledger_000\`;
CREATE TABLE IF NOT EXISTS \`${schema}\`.\`raffle_quota_decrement_ledger_003\`
  LIKE \`${schema}\`.\`raffle_quota_decrement_ledger_000\`;
SQL
}

ensure_ledger big_market_01
ensure_ledger big_market_02

export BM_BACKEND=mysql
export BM_MYSQL_URL
export BM_DATA_DIR="${BM_DATA_DIR:-$ROOT/big-market-rs/target/run/mysql-data}"

pkill -f '[t]arget/release/bm-app' 2>/dev/null || true
pkill -f '[t]arget/release/bm-gateway' 2>/dev/null || true
sleep 0.3

"$ROOT/scripts/run-rust-stack.sh"

QUOTA_BEFORE="$(mysql -N -h"$MYSQL_HOST" -P"$MYSQL_PORT" -u"$MYSQL_USER" -p"$MYSQL_PASS" "$MYSQL_DB" \
  -e "SELECT total_count_surplus FROM big_market_01.raffle_activity_account \
      WHERE user_id='xiaofuge' AND activity_id=100401 LIMIT 1" 2>/dev/null || echo "")"

"$ROOT/scripts/smoke-rust-api.sh"

if [ -n "$QUOTA_BEFORE" ]; then
  QUOTA_AFTER="$(mysql -N -h"$MYSQL_HOST" -P"$MYSQL_PORT" -u"$MYSQL_USER" -p"$MYSQL_PASS" "$MYSQL_DB" \
    -e "SELECT total_count_surplus FROM big_market_01.raffle_activity_account \
        WHERE user_id='xiaofuge' AND activity_id=100401 LIMIT 1")"
  echo "  INFO  xiaofuge@100401 quota surplus: ${QUOTA_BEFORE} -> ${QUOTA_AFTER} (MySQL-backed)"
fi

echo
echo "Rust MySQL smoke passed."

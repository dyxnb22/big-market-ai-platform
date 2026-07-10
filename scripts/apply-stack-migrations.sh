#!/usr/bin/env bash
# Unified stack migrations: reconcile DDL + XXL seeds + post-checks.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

MYSQL_CONTAINER="${MYSQL_CONTAINER:-mysql}"
MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:-123456}"
STAMP_FILE="${STACK_MIGRATION_STAMP:-data/stack-migration-stamp}"

echo "=== Stack migrations ==="

./scripts/apply-reconcile-ddl.sh
./scripts/apply-xxl-job-seeds.sh

echo "Verifying migration artifacts..."

mysql_query() {
  docker exec "$MYSQL_CONTAINER" mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -N -e "$1" 2>/dev/null
}

for schema in big_market_01 big_market_02; do
  col=$(mysql_query "SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema='${schema}' AND table_name='chat_credit_session' AND column_name='deduct_state';")
  if [ "${col:-0}" != "1" ]; then
    echo "FAIL: ${schema}.chat_credit_session.deduct_state missing" >&2
    exit 1
  fi
  echo "  OK  ${schema}.chat_credit_session.deduct_state"
done

for table in strategy_award_stock_decrement_ledger activity_sku_stock_decrement_ledger; do
  cnt=$(mysql_query "SELECT COUNT(*) FROM information_schema.tables
    WHERE table_schema='big_market' AND table_name='${table}';")
  if [ "${cnt:-0}" != "1" ]; then
    echo "FAIL: big_market.${table} missing" >&2
    exit 1
  fi
  echo "  OK  big_market.${table}"
done

xxl_cnt=$(mysql_query "SELECT COUNT(*) FROM xxl_job.xxl_job_info
  WHERE executor_handler='ChatRefundReconcileJob';")
if [ "${xxl_cnt:-0}" -lt 1 ]; then
  echo "FAIL: xxl_job ChatRefundReconcileJob seed missing" >&2
  exit 1
fi
echo "  OK  xxl_job ChatRefundReconcileJob"

mkdir -p "$(dirname "$STAMP_FILE")"
GIT_HEAD=$(git rev-parse --short HEAD 2>/dev/null || echo "unknown")
DATE_ISO=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
printf '%s %s\n' "$DATE_ISO" "$GIT_HEAD" > "$STAMP_FILE"
echo "  Wrote $STAMP_FILE ($DATE_ISO $GIT_HEAD)"
echo "=== Stack migrations OK ==="

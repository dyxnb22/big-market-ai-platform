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

NACOS_PLATFORM_SQL="$ROOT/docs/dev-ops/mysql/sql/z-nacos-platform-config.sql"
if [ ! -f "$NACOS_PLATFORM_SQL" ]; then
  echo "FAIL: Nacos platform seed SQL missing: $NACOS_PLATFORM_SQL" >&2
  exit 1
fi
docker exec -i "$MYSQL_CONTAINER" mysql -uroot -p"$MYSQL_ROOT_PASSWORD" < "$NACOS_PLATFORM_SQL"
echo "Nacos platform DataIds ensured without overwriting existing configuration."

# The upstream XXL init SQL has a learning-only admin/123456 row. When the
# operator supplies both environment values, rotate it idempotently before any
# acceptance smoke logs in. Values are quoted for a MySQL string literal and
# are never printed.
if [ -n "${XXL_JOB_ADMIN_USER:-}" ] || [ -n "${XXL_JOB_ADMIN_PASSWORD:-}" ]; then
  : "${XXL_JOB_ADMIN_USER:?XXL_JOB_ADMIN_USER is required when rotating XXL credentials}"
  : "${XXL_JOB_ADMIN_PASSWORD:?XXL_JOB_ADMIN_PASSWORD is required when rotating XXL credentials}"
  xxl_user_sql=$(printf '%s' "$XXL_JOB_ADMIN_USER" | sed "s/'/''/g")
  xxl_password_sql=$(printf '%s' "$XXL_JOB_ADMIN_PASSWORD" | sed "s/'/''/g")
  docker exec "$MYSQL_CONTAINER" mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -e \
    "UPDATE xxl_job.xxl_job_user SET username='${xxl_user_sql}', password=MD5('${xxl_password_sql}') WHERE id=1;"
  echo "XXL administrator credentials rotated from environment."
fi

FREEZE_SQL="$ROOT/docs/dev-ops/mysql/sql/z-learning-freeze-demo.sql"
if [ ! -f "$FREEZE_SQL" ]; then
  echo "FAIL: learning-freeze demo SQL missing: $FREEZE_SQL" >&2
  exit 1
fi
docker exec -i "$MYSQL_CONTAINER" mysql -uroot -p"$MYSQL_ROOT_PASSWORD" < "$FREEZE_SQL"
echo "Learning-freeze demo seed alignment applied."

# Reused volumes may still hold the old 10007 award list/probability map.
# Delete only derived strategy caches; armory rebuilds them from the aligned DB
# seed before smoke/Playwright. Stock counters are intentionally preserved.
REDIS_CONTAINER="${REDIS_CONTAINER:-redis}"
if ! docker exec "$REDIS_CONTAINER" redis-cli DEL \
  big_market_strategy_award_list_key_10007 \
  big_market_strategy_rate_table_key_10007 \
  big_market_strategy_rate_range_key_10007 \
  strategy_armory_algorithm_key_10007 >/dev/null; then
  echo "FAIL: could not invalidate staged strategy 10007 Redis caches" >&2
  exit 1
fi
echo "Staged strategy 10007 derived Redis caches invalidated."

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

  quota_ledger_count=$(mysql_query "SELECT COUNT(*) FROM information_schema.tables
    WHERE table_schema='${schema}'
      AND table_name IN ('raffle_quota_decrement_ledger_000', 'raffle_quota_decrement_ledger_001',
                         'raffle_quota_decrement_ledger_002', 'raffle_quota_decrement_ledger_003');")
  if [ "${quota_ledger_count:-0}" != "4" ]; then
    echo "FAIL: ${schema} quota decrement ledger shards are incomplete" >&2
    exit 1
  fi
  echo "  OK  ${schema}.raffle_quota_decrement_ledger_000..003"
done

for table in strategy_award_stock_decrement_ledger activity_sku_stock_decrement_ledger activity_sku_stock_restore_ledger; do
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

if [ -n "${XXL_JOB_ADMIN_USER:-}" ]; then
  xxl_user_count=$(mysql_query "SELECT COUNT(*) FROM xxl_job.xxl_job_user
    WHERE username='${xxl_user_sql}' AND password=MD5('${xxl_password_sql}');")
  if [ "${xxl_user_count:-0}" != "1" ]; then
    echo "FAIL: XXL administrator credential rotation did not persist" >&2
    exit 1
  fi
  echo "  OK  XXL administrator credentials from environment"
fi

nacos_config_count=$(mysql_query "SELECT COUNT(*) FROM nacos_config.config_info
  WHERE (data_id='big-market-platform-config' AND group_id='DEFAULT_GROUP')
     OR (data_id='big-market-runtime-switches' AND group_id='DEFAULT_GROUP');")
if [ "${nacos_config_count:-0}" != "2" ]; then
  echo "FAIL: required Nacos platform DataIds are missing" >&2
  exit 1
fi
echo "  OK  Nacos platform and runtime DataIds"

demo_rate=$(mysql_query "SELECT CONCAT(
    SUM(CASE WHEN award_id=101 AND award_rate=1.0000 THEN 1 ELSE 0 END), ':',
    SUM(CASE WHEN award_id<>101 AND award_rate<>0.0000 THEN 1 ELSE 0 END))
  FROM big_market.strategy_award WHERE strategy_id=10007;")
if [ "$demo_rate" != "1:0" ]; then
  echo "FAIL: staged strategy 10007 is not deterministic local-credit (got $demo_rate)" >&2
  exit 1
fi
echo "  OK  staged strategy 10007 deterministic award 101"

dispatch_jobs=$(mysql_query "SELECT COUNT(*) FROM xxl_job.xxl_job_info
  WHERE executor_handler IN ('DispatchCreditAwardTaskJob_DB1','DispatchCreditAwardTaskJob_DB2')
    AND trigger_status=1;")
if [ "${dispatch_jobs:-0}" != "2" ]; then
  echo "FAIL: award-credit dispatch jobs are not both enabled" >&2
  exit 1
fi
echo "  OK  award-credit outbox dispatch jobs enabled"

mkdir -p "$(dirname "$STAMP_FILE")"
GIT_HEAD=$(git rev-parse --short HEAD 2>/dev/null || echo "unknown")
DATE_ISO=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
printf '%s %s\n' "$DATE_ISO" "$GIT_HEAD" > "$STAMP_FILE"
echo "  Wrote $STAMP_FILE ($DATE_ISO $GIT_HEAD)"
echo "=== Stack migrations OK ==="

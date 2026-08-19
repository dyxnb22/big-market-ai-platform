#!/usr/bin/env bash
# 将一条策略库存确认任务从 manual_pending 移回 pending。
set -euo pipefail

usage() { echo "Usage: $0 [--dry-run] <user-id> <order-id>" >&2; exit 2; }
dry_run=false
if [ "${1:-}" = "--dry-run" ]; then dry_run=true; shift; fi
[ "$#" -eq 2 ] || usage
user_id="$1"
order_id="$2"
case "$user_id" in *[!A-Za-z0-9._:-]*|'') echo "Invalid user-id" >&2; exit 2;; esac
case "$order_id" in *[!A-Za-z0-9._:-]*|'') echo "Invalid order-id" >&2; exit 2;; esac

mysql_container="${MYSQL_CONTAINER:-mysql}"
mysql_root_password="${MYSQL_ROOT_PASSWORD:-123456}"
for database in big_market_01 big_market_02; do
  if [ "$dry_run" = true ]; then
    count="$(docker exec "$mysql_container" mysql -uroot -p"$mysql_root_password" -N -s -e \
      "SELECT COUNT(*) FROM ${database}.strategy_award_stock_confirm_task WHERE user_id='${user_id}' AND order_id='${order_id}' AND state='manual_pending';" 2>/dev/null || echo 0)"
    echo "${database}: ${count} manual_pending row(s) eligible for replay"
  else
    updated="$(docker exec "$mysql_container" mysql -uroot -p"$mysql_root_password" -N -s -e \
      "UPDATE ${database}.strategy_award_stock_confirm_task SET state='pending', retry_count=0, update_time=NOW() WHERE user_id='${user_id}' AND order_id='${order_id}' AND state='manual_pending'; SELECT ROW_COUNT();" 2>/dev/null | tail -n 1)"
    [ "${updated:-0}" -eq 0 ] || echo "${database}: replayed ${updated} row(s)"
  fi
done

if [ "$dry_run" = true ]; then
  echo "Dry run only; no rows changed."
else
  echo "Replay requested. Trigger StrategyAwardStockConfirmJob_DB1/DB2 or wait for the next schedule."
fi

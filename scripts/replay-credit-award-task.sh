#!/usr/bin/env bash
# 在账户服务/RPC 根因修复后，重放一条失败的积分发放 Outbox 记录。
set -euo pipefail

usage() { echo "Usage: $0 [--dry-run] <user-id> <award-order-id>" >&2; exit 2; }
dry_run=false
if [ "${1:-}" = "--dry-run" ]; then dry_run=true; shift; fi
[ "$#" -eq 2 ] || usage
user_id="$1"
award_order_id="$2"
case "$user_id" in *[!A-Za-z0-9._:-]*|'') echo "Invalid user-id" >&2; exit 2;; esac
case "$award_order_id" in *[!A-Za-z0-9._:-]*|'') echo "Invalid award-order-id" >&2; exit 2;; esac

mysql_container="${MYSQL_CONTAINER:-mysql}"
mysql_root_password="${MYSQL_ROOT_PASSWORD:-123456}"
for database in big_market_01 big_market_02; do
  for table_suffix in 000 001 002 003; do
    table="${database}.credit_award_task_${table_suffix}"
    if [ "$dry_run" = true ]; then
      count="$(docker exec "$mysql_container" mysql -uroot -p"$mysql_root_password" -N -s -e \
        "SELECT COUNT(*) FROM ${table} WHERE user_id='${user_id}' AND award_order_id='${award_order_id}' AND state='failed';" 2>/dev/null || echo 0)"
      echo "${table}: ${count} failed row(s) eligible for replay"
    else
      updated="$(docker exec "$mysql_container" mysql -uroot -p"$mysql_root_password" -N -s -e \
        "UPDATE ${table} SET state='pending', retry_count=0, update_time=NOW() WHERE user_id='${user_id}' AND award_order_id='${award_order_id}' AND state='failed'; SELECT ROW_COUNT();" 2>/dev/null | tail -n 1)"
      [ "${updated:-0}" -eq 0 ] || echo "${table}: replayed ${updated} row(s)"
    fi
  done
done

if [ "$dry_run" = true ]; then
  echo "Dry run only; no rows changed."
else
  echo "Replay requested. Trigger DispatchCreditAwardTaskJob_DB1/DB2 or wait for the next schedule."
fi

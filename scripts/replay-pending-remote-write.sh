#!/usr/bin/env bash
# Explicit operator replay for a remote-write task exhausted by RemoteWriteReconcileJob.
# It preserves out_business_no and payload, resetting only failed -> pending after the
# underlying RPC/Nacos/DB problem has been resolved.
set -euo pipefail

usage() {
  echo "Usage: $0 [--dry-run] <out-business-no> <credit_create|quota_create|quota_update|quota_rollback>" >&2
  exit 2
}

dry_run=false
if [ "${1:-}" = "--dry-run" ]; then
  dry_run=true
  shift
fi

[ "$#" -eq 2 ] || usage
out_business_no="$1"
operation="$2"

case "$operation" in
  credit_create|quota_create|quota_update|quota_rollback) ;;
  *) usage ;;
esac

# Business keys are generated identifiers. Reject anything that could change the
# operator SQL rather than attempting shell or SQL escaping in an incident tool.
case "$out_business_no" in
  *[!A-Za-z0-9._:-]*|'')
    echo "Invalid out-business-no; use the exact generated identifier." >&2
    exit 2
    ;;
esac

mysql_container="${MYSQL_CONTAINER:-mysql}"
mysql_root_password="${MYSQL_ROOT_PASSWORD:-123456}"

for database in big_market big_market_01 big_market_02; do
  count="$(docker exec "$mysql_container" mysql -uroot -p"$mysql_root_password" -N -s -e \
    "SELECT COUNT(*) FROM ${database}.pending_remote_write_task WHERE out_business_no='${out_business_no}' AND operation='${operation}' AND state='failed';" 2>/dev/null)"
  [ -n "$count" ] || count=0
  if [ "$dry_run" = true ]; then
    echo "${database}: ${count} failed task(s) eligible for replay"
    continue
  fi
  updated="$(docker exec "$mysql_container" mysql -uroot -p"$mysql_root_password" -N -s -e \
    "UPDATE ${database}.pending_remote_write_task SET state='pending', retry_count=0, update_time=NOW() WHERE out_business_no='${out_business_no}' AND operation='${operation}' AND state='failed'; SELECT ROW_COUNT();" 2>/dev/null | tail -n 1)"
  echo "${database}: replayed ${updated:-0} task(s)"
done

if [ "$dry_run" = true ]; then
  echo "Dry run only; no rows changed. Resolve the root cause before replaying."
else
  echo "Replay requested. Trigger RemoteWriteReconcileJob or wait for its next schedule."
fi

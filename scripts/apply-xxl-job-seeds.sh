#!/usr/bin/env bash
# Seed XXL-Job handlers 7–13 on existing MySQL volumes (idempotent).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SQL_FILE="$ROOT/docs/dev-ops/mysql/sql/z-xxl-job-extra-handlers.sql"
MYSQL_CONTAINER="${MYSQL_CONTAINER:-mysql}"
MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:-123456}"

if [ ! -f "$SQL_FILE" ]; then
  echo "Missing XXL seed SQL: $SQL_FILE" >&2
  exit 1
fi

if ! docker ps --format '{{.Names}}' | grep -qx "$MYSQL_CONTAINER"; then
  echo "MySQL container '$MYSQL_CONTAINER' is not running." >&2
  exit 1
fi

docker exec -i "$MYSQL_CONTAINER" mysql -uroot -p"$MYSQL_ROOT_PASSWORD" < "$SQL_FILE"

docker exec "$MYSQL_CONTAINER" mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -N -e \
  "SELECT id, executor_handler FROM xxl_job.xxl_job_info
   WHERE executor_handler IN ('ChatRefundReconcileJob','RemoteWriteReconcileJob','DlqReplayJob')
   ORDER BY id;"

echo "XXL extra job handlers seeded."

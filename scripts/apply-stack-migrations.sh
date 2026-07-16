#!/usr/bin/env bash
# Apply Rust-era MySQL reconcile DDL (no XXL / Java seeds).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

MYSQL_CONTAINER="${MYSQL_CONTAINER:-mysql}"
MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:-123456}"

echo "=== Stack migrations (Rust reconcile) ==="

if [ -x ./scripts/apply-reconcile-ddl.sh ]; then
  ./scripts/apply-reconcile-ddl.sh
else
  echo "WARN: apply-reconcile-ddl.sh missing; skipping" >&2
fi

RECONCILE_SQL="$ROOT/docs/dev-ops/mysql/sql/z-reconcile-tables.sql"
if [ -f "$RECONCILE_SQL" ]; then
  if docker ps --format '{{.Names}}' | grep -qx "$MYSQL_CONTAINER"; then
    docker exec -i "$MYSQL_CONTAINER" mysql -uroot -p"$MYSQL_ROOT_PASSWORD" < "$RECONCILE_SQL"
    echo "  OK  applied z-reconcile-tables.sql"
  else
    echo "  SKIP  mysql container '$MYSQL_CONTAINER' not running"
  fi
fi

echo "Stack migrations done (XXL/Java seeds are not part of this project)."

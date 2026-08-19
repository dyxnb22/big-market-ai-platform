#!/usr/bin/env bash
# 使用 promtool 校验 Prometheus 配置和告警规则，并静态检查 8080–8086 目标端口。
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

PROM_YML="$ROOT/docs/dev-ops/prometheus/prometheus.yml"
RULES_YML="$ROOT/docs/dev-ops/prometheus/rules/big-market-alerts.yml"
PROM_MOUNT="$ROOT/docs/dev-ops/prometheus"

if [ ! -f "$PROM_YML" ]; then
  echo "Missing $PROM_YML" >&2
  exit 1
fi

promtool_cmd() {
  if command -v promtool >/dev/null 2>&1; then
    promtool "$@"
    return $?
  fi
  if command -v docker >/dev/null 2>&1; then
    docker run --rm --entrypoint promtool \
      -v "$PROM_MOUNT:/etc/prometheus:ro" \
      prom/prometheus:v2.53.0 "$@"
    return $?
  fi
  echo "WARN: promtool not found (install promtool or Docker); skipping promtool checks" >&2
  return 0
}

echo "=== Prometheus config validation ==="

if command -v promtool >/dev/null 2>&1; then
  promtool check config "$PROM_YML"
  echo "  OK  promtool check config"
  if [ -f "$RULES_YML" ]; then
    promtool check rules "$RULES_YML"
    echo "  OK  promtool check rules"
  fi
elif command -v docker >/dev/null 2>&1; then
  promtool_cmd check config /etc/prometheus/prometheus.yml
  echo "  OK  promtool check config (docker)"
  if [ -f "$RULES_YML" ]; then
    promtool_cmd check rules /etc/prometheus/rules/big-market-alerts.yml
    echo "  OK  promtool check rules (docker)"
  fi
else
  echo "WARN: promtool not found; static target check only" >&2
fi

missing=0
for port in 8080 8081 8082 8083 8084 8085 8086; do
  if ! grep -q ":${port}'" "$PROM_YML"; then
    echo "  FAIL  prometheus.yml missing scrape target port $port" >&2
    missing=1
  fi
done
if [ "$missing" -ne 0 ]; then
  exit 1
fi
echo "  OK  scrape targets 8080-8086 present"

echo "=== Prometheus validation OK ==="

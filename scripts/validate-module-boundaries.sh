#!/usr/bin/env bash
# Static guard for the fixed service topology: market owns HTTP/business paths;
# message-job owns MQ consumers and XXL jobs.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

MARKET_POM="big-market-market-service/pom.xml"
MARKET_SRC="big-market-market-service/src/main/java"

if rg -U -q '<dependency>\s*<groupId>com\.xuxueli</groupId>\s*<artifactId>xxl-job-core</artifactId>' "$MARKET_POM"; then
  echo "FAIL: market-service must not declare xxl-job-core" >&2
  exit 1
fi
if ! rg -n '<artifactId>xxl-job-core</artifactId>' -A4 -B4 "$MARKET_POM" | rg -q 'exclusion'; then
  echo "FAIL: market-service trigger dependency must explicitly exclude xxl-job-core" >&2
  exit 1
fi
if rg -U -q '<dependency>\s*<groupId>org\.springframework\.boot</groupId>\s*<artifactId>spring-boot-starter-amqp</artifactId>' "$MARKET_POM"; then
  echo "FAIL: market-service must not declare spring-boot-starter-amqp" >&2
  exit 1
fi
if ! rg -n '<artifactId>spring-boot-starter-amqp</artifactId>' -A4 -B4 "$MARKET_POM" | rg -q 'exclusion'; then
  echo "FAIL: market-service trigger dependency must explicitly exclude spring-boot-starter-amqp" >&2
  exit 1
fi

if rg -n -q 'com\.dyx\.market\.trigger\.(job|listener)|@XxlJob|@RabbitListener' "$MARKET_SRC"; then
  echo "FAIL: market-service contains message-job-owned trigger handlers" >&2
  exit 1
fi

echo "Module boundaries OK: market has no XXL/MQ handler ownership."

#!/usr/bin/env bash
# 固定服务拓扑的静态门禁：market 负责 HTTP/业务路径；
# message-job 负责 MQ 消费者和 XXL 任务。
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

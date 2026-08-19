#!/usr/bin/env bash
# 从干净构建到冒烟测试，验证完整微服务栈。
#
# 用法：
#   ./scripts/validate-microservices-stack.sh [--start-stack] [--skip-build]
#   ./scripts/validate-microservices-stack.sh [--skip-docker] [--skip-build]  # 旧版别名
#
# 选项：
#   --start-stack   通过 docker compose 启动基础设施+应用（显式选择；默认不启动）
#   --skip-docker   旧版别名：不启动 Docker（与默认行为相同）
#   --skip-build    跳过 mvn verify（JAR 已构建时有用）
#
# 默认情况下本脚本不会自动启动 Docker。请自行启动服务栈：
#   docker compose -f docs/dev-ops/docker-compose-environment.yml up -d mysql redis rabbitmq nacos xxl-job-admin elasticsearch
#   ./scripts/apply-stack-migrations.sh
#   docker compose up --build -d
# 然后运行本脚本（仅执行健康轮询和冒烟测试）。
#
# 前置条件：
#   - JDK 17+, Maven 3.x
#   - Docker + Docker Compose v2（使用 --start-stack 或针对本地栈执行冒烟测试时需要）
#   - python3（冒烟测试用于解析 JSON）
#
# 重要：本脚本不会销毁数据，也不会清空队列。
# 如果启动时看到 RabbitMQ 的 "inequivalent arg" 错误，请查看下方说明。

set -euo pipefail

START_STACK=false
SKIP_BUILD=false
HOST=""

for arg in "$@"; do
  case "$arg" in
    --start-stack) START_STACK=true ;;
    --skip-docker) START_STACK=false ;;
    --skip-build)  SKIP_BUILD=true ;;
    --*) echo "Unknown option: $arg"; exit 1 ;;
    *) HOST="$arg" ;;
  esac
done

HOST="${HOST:-localhost}"

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
# shellcheck source=lib/health-poll.sh
source "$ROOT/scripts/lib/health-poll.sh"
# shellcheck source=lib/java17-precheck.sh
source "$ROOT/scripts/lib/java17-precheck.sh"
require_java_17

echo "================================================================="
echo "  big-market microservices stack validation"
echo "  start_stack=${START_STACK}  $(date)"
echo "================================================================="
echo ""

# ── 步骤 1：构建 ──────────────────────────────────────────────────────────────

if [ "$SKIP_BUILD" = false ]; then
  echo "Step 1/4: Building all modules (mvn verify)"
  echo "-----------------------------------------------------------------"
  if ! mvn verify -DfailIfNoTests=false; then
    echo ""
    echo "BUILD FAILED. Fix compilation errors before proceeding."
    echo ""
    echo "Useful commands:"
    echo "  mvn clean package -DskipTests -pl big-market-market-service   # build one module"
    echo "  mvn dependency:tree -pl big-market-message-job-service        # inspect deps"
    exit 1
  fi
  echo ""
  echo "  Build successful."
  echo ""
else
  echo "Step 1/4: SKIPPED (--skip-build)"
  echo ""
fi

# ── 步骤 2：基础设施栈 ──────────────────────────────────────────────────────────────

if [ "$START_STACK" = true ]; then
  echo "Step 2/4: Starting infrastructure stack"
  echo "-----------------------------------------------------------------"
  echo "  docker compose -f docs/dev-ops/docker-compose-environment.yml up -d mysql redis rabbitmq nacos xxl-job-admin elasticsearch"
  if ! docker compose -f docs/dev-ops/docker-compose-environment.yml up -d mysql redis rabbitmq nacos xxl-job-admin elasticsearch; then
    echo ""
    echo "Infrastructure stack failed to start."
    echo ""
    echo "Useful commands:"
    echo "  docker compose -f docs/dev-ops/docker-compose-environment.yml ps"
    echo "  docker compose -f docs/dev-ops/docker-compose-environment.yml logs rabbitmq"
    echo "  docker compose -f docs/dev-ops/docker-compose-environment.yml logs mysql"
    exit 1
  fi
  echo ""
  echo "  Infrastructure stack started. Applying stack migrations (idempotent)..."
  if ! ./scripts/apply-stack-migrations.sh; then
    echo ""
    echo "Stack migrations failed. Old MySQL volumes may need reconcile DDL / XXL seeds."
    echo "  ./scripts/apply-stack-migrations.sh"
    exit 1
  fi
  wait_for_http_up "http://${HOST}:3306" 60 "mysql-port" || true
  echo "  Waiting for MySQL to accept connections..."
  for _ in $(seq 1 30); do
    if docker exec mysql mysqladmin ping -uroot -p123456 --silent 2>/dev/null; then
      echo "  UP  mysql"
      break
    fi
    sleep 2
  done
  echo ""
else
  echo "Step 2/4: SKIPPED (no --start-stack; Docker will not be started)"
  if docker exec mysql mysqladmin ping -uroot -p123456 --silent 2>/dev/null; then
    echo "  MySQL reachable — applying stack migrations..."
    if ! ./scripts/apply-stack-migrations.sh; then
      echo "Stack migrations failed."
      exit 1
    fi
  else
    echo "  MySQL not reachable. Start infra manually or pass --start-stack."
  fi
  echo ""
fi

# ── 步骤 3：应用栈 ──────────────────────────────────────────────────

if [ "$START_STACK" = true ]; then
  echo "Step 3/4: Building and starting application services"
  echo "-----------------------------------------------------------------"
  echo "  docker compose up --build -d"
  if ! docker compose up --build -d; then
    echo ""
    echo "Application stack failed to start."
    echo ""
    echo "Useful commands:"
    echo "  docker compose ps"
    echo "  docker compose logs big-market-market-service"
    echo "  docker compose logs big-market-message-job-service"
    echo "  docker compose logs big-market-gateway"
    echo ""
    echo "If you see 'inequivalent arg' errors in RabbitMQ logs:"
    echo "  RabbitMQ queue arguments are IMMUTABLE once a queue is created."
    echo "  Reset local queues only on a local dev environment (see docs/operations-checklist.md)."
    exit 1
  fi
  echo ""
  echo "  Application stack started. Polling health endpoints..."
  if ! wait_for_stack_healthy "$HOST" 180; then
    echo ""
    echo "Stack health polling timed out."
    echo "  docker compose ps"
    echo "  docker compose logs --tail=50 big-market-gateway"
    exit 1
  fi
  echo ""
else
  echo "Step 3/4: SKIPPED (no --start-stack) — polling existing stack health..."
  if ! wait_for_stack_healthy "$HOST" 60; then
    echo ""
    echo "Stack is not healthy and --start-stack was not set (no auto-start)."
    echo ""
    echo "Start manually:"
    echo "  docker compose -f docs/dev-ops/docker-compose-environment.yml up -d mysql redis rabbitmq nacos xxl-job-admin elasticsearch"
    echo "  ./scripts/apply-stack-migrations.sh"
    echo "  docker compose up --build -d"
    echo ""
    echo "Or: ./scripts/validate-microservices-stack.sh --start-stack --skip-build"
    exit 1
  fi
  echo ""
fi

# ── 步骤 4：冒烟测试 ─────────────────────────────────────────────────────────

echo "Step 4/4: Running smoke test"
echo "-----------------------------------------------------------------"
chmod +x ./scripts/ensure-demo-activity-online.sh
if ! ./scripts/ensure-demo-activity-online.sh "http://${HOST}:8080"; then
  echo ""
  echo "Demo activity online ensure FAILED (fail-closed)."
  exit 1
fi
if ! ./scripts/smoke-test-microservices.sh "$HOST"; then
  echo ""
  echo "SMOKE TEST FAILED. Check the failures above, then:"
  echo ""
  echo "  docker compose ps                                       # container status"
  echo "  docker compose logs --tail=50 big-market-gateway       # gateway logs"
  echo "  docker compose logs --tail=50 big-market-market-service # market logs"
  echo "  docker compose logs --tail=50 big-market-auth-service  # auth logs"
  echo "  docker compose logs --tail=50 big-market-message-job-service # job logs"
  echo ""
  echo "  # Re-run just the smoke test after fixing:"
  echo "  ./scripts/smoke-test-microservices.sh"
  exit 1
fi

echo ""
echo "================================================================="
echo "  ALL CHECKS PASSED. Stack is healthy."
echo "================================================================="
echo ""
echo "Next steps:"
echo "  - To watch live logs:      docker compose logs -f"
echo "  - To stop the app stack:   docker compose down"
echo "  - To stop all infra:       docker compose -f docs/dev-ops/docker-compose-environment.yml down"
echo "  - For architecture notes: see docs/MICROSERVICES.md"

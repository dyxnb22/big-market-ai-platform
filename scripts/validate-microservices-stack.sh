#!/usr/bin/env bash
# Validate the full microservices stack from a clean build through smoke test.
#
# Usage:
#   ./scripts/validate-microservices-stack.sh [--start-stack] [--skip-build]
#   ./scripts/validate-microservices-stack.sh [--skip-docker] [--skip-build]  # legacy alias
#
# Options:
#   --start-stack   Start infra + app via docker compose (opt-in; default does NOT start)
#   --skip-docker   Legacy alias: do not start docker (same as default)
#   --skip-build    Skip mvn verify (useful when JARs are already built)
#
# By default this script does NOT auto-start Docker. Start the stack yourself:
#   docker compose -f docs/dev-ops/docker-compose-environment.yml up -d
#   ./scripts/apply-stack-migrations.sh
#   docker compose up --build -d
# Then run this script (health poll + smoke only).
#
# Prerequisites:
#   - JDK 8, Maven 3.x
#   - Docker + Docker Compose v2 (when using --start-stack or for smoke against local stack)
#   - python3 (used by smoke test for JSON parsing)
#
# IMPORTANT: This script does NOT destroy data. It does NOT purge queues.
# If you see RabbitMQ "inequivalent arg" errors on startup, see the note below.

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

echo "================================================================="
echo "  big-market microservices stack validation"
echo "  start_stack=${START_STACK}  $(date)"
echo "================================================================="
echo ""

# ── Step 1: Build ──────────────────────────────────────────────────────────────

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

# ── Step 2: Infrastructure stack ──────────────────────────────────────────────

if [ "$START_STACK" = true ]; then
  echo "Step 2/4: Starting infrastructure stack"
  echo "-----------------------------------------------------------------"
  echo "  docker compose -f docs/dev-ops/docker-compose-environment.yml up -d"
  if ! docker compose -f docs/dev-ops/docker-compose-environment.yml up -d; then
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

# ── Step 3: Application stack ──────────────────────────────────────────────────

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
    echo "  docker compose -f docs/dev-ops/docker-compose-environment.yml up -d"
    echo "  ./scripts/apply-stack-migrations.sh"
    echo "  docker compose up --build -d"
    echo ""
    echo "Or: ./scripts/validate-microservices-stack.sh --start-stack --skip-build"
    exit 1
  fi
  echo ""
fi

# ── Step 4: Smoke test ─────────────────────────────────────────────────────────

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

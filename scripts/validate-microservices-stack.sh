#!/usr/bin/env bash
# Validate the full microservices stack from a clean build through smoke test.
#
# Usage:
#   ./scripts/validate-microservices-stack.sh [--skip-docker] [--skip-build]
#
# Options:
#   --skip-docker   Skip docker compose steps (useful when infra is already running)
#   --skip-build    Skip mvn clean package (useful when JARs are already built)
#
# Prerequisites:
#   - JDK 8, Maven 3.x
#   - Docker + Docker Compose v2
#   - python3 (used by smoke test for JSON parsing)
#
# IMPORTANT: This script does NOT destroy data. It does NOT purge queues.
# If you see RabbitMQ "inequivalent arg" errors on startup, see the note below.

set -euo pipefail

SKIP_DOCKER=false
SKIP_BUILD=false
HOST=""

for arg in "$@"; do
  case "$arg" in
    --skip-docker) SKIP_DOCKER=true ;;
    --skip-build)  SKIP_BUILD=true ;;
    --*) echo "Unknown option: $arg"; exit 1 ;;
    *) HOST="$arg" ;;
  esac
done

HOST="${HOST:-localhost}"

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

echo "================================================================="
echo "  big-market microservices stack validation"
echo "  $(date)"
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

if [ "$SKIP_DOCKER" = false ]; then
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
  echo "  Infrastructure stack started. Applying reconcile DDL (idempotent)..."
  if ! ./scripts/apply-reconcile-ddl.sh; then
    echo ""
    echo "Reconcile DDL apply failed. chat_credit_session may be missing on old MySQL volumes."
    echo "  ./scripts/apply-reconcile-ddl.sh"
    exit 1
  fi
  if ! ./scripts/apply-xxl-job-seeds.sh; then
    echo ""
    echo "XXL job seed apply failed. ChatRefundReconcileJob may be missing on old volumes."
    echo "  ./scripts/apply-xxl-job-seeds.sh"
    exit 1
  fi
  echo "  Waiting 10s for services to settle..."
  sleep 10
  echo ""
else
  echo "Step 2/4: SKIPPED (--skip-docker)"
  echo ""
fi

# ── Step 3: Application stack ──────────────────────────────────────────────────

if [ "$SKIP_DOCKER" = false ]; then
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
    # ── RabbitMQ queue argument conflict note ────────────────────────────────
    echo "If you see 'inequivalent arg' errors in RabbitMQ logs:"
    echo "  RabbitMQ queue arguments (x-dead-letter-exchange, x-message-ttl, etc.)"
    echo "  are IMMUTABLE once a queue is created. If the DLQ configuration was"
    echo "  changed, the existing queues must be deleted before the new declarations"
    echo "  take effect. The safest way to reset local dev queues is:"
    echo ""
    echo "    # 1. Stop the app stack first"
    echo "    docker compose down"
    echo ""
    echo "    # 2. Open the RabbitMQ management UI at http://localhost:15672"
    echo "    #    (login: admin / admin) and manually delete the conflicting queues."
    echo "    #    OR stop and remove only the RabbitMQ container/volume:"
    echo "    docker compose -f docs/dev-ops/docker-compose-environment.yml stop rabbitmq"
    echo "    docker compose -f docs/dev-ops/docker-compose-environment.yml rm -f rabbitmq"
    echo "    docker volume rm dev-ops_rabbitmq-data  # adjust volume name if different"
    echo ""
    echo "    # 3. Restart the infra stack"
    echo "    docker compose -f docs/dev-ops/docker-compose-environment.yml up -d"
    echo ""
    echo "    # 4. Re-run this script"
    echo "    ./scripts/validate-microservices-stack.sh --skip-build"
    echo ""
    echo "  WARNING: deleting queues discards unprocessed messages. Only do this"
    echo "  on a local dev environment, never on a shared or production broker."
    exit 1
  fi
  echo ""
  echo "  Application stack started. Waiting 30s for health checks..."
  sleep 30
  echo ""
else
  echo "Step 3/4: SKIPPED (--skip-docker)"
  echo ""
fi

# ── Step 4: Smoke test ─────────────────────────────────────────────────────────

echo "Step 4/4: Running smoke test"
echo "-----------------------------------------------------------------"
chmod +x ./scripts/ensure-demo-activity-online.sh 2>/dev/null || true
./scripts/ensure-demo-activity-online.sh "http://${HOST}:8080" || echo "WARN: demo activity online ensure skipped"
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

#!/usr/bin/env bash
# One-click acceptance: build → migrate → stage activity online → smoke →
# chat refund E2E → Playwright. Fail-closed.
#
# Usage:
#   ./scripts/acceptance.sh              # --reuse (default): assume stack may be up
#   ./scripts/acceptance.sh --reuse      # health poll + gates only
#   ./scripts/acceptance.sh --fresh --confirm-destroy-volumes
#   ./scripts/acceptance.sh --skip-build --skip-playwright
#
# --fresh requires --confirm-destroy-volumes to delete compose volumes.

set -euo pipefail

MODE="reuse"
SKIP_BUILD=false
SKIP_PLAYWRIGHT=false
SKIP_SECURITY=true
CONFIRM_DESTROY=false
HOST="${HOST:-localhost}"
RUN_SECURE=false

for arg in "$@"; do
  case "$arg" in
    --fresh) MODE="fresh" ;;
    --reuse) MODE="reuse" ;;
    --skip-build) SKIP_BUILD=true ;;
    --skip-playwright) SKIP_PLAYWRIGHT=true ;;
    --secure) RUN_SECURE=true; SKIP_SECURITY=false ;;
    --confirm-destroy-volumes) CONFIRM_DESTROY=true ;;
    --help|-h)
      sed -n '2,14p' "$0"
      exit 0
      ;;
    *)
      echo "Unknown option: $arg" >&2
      exit 1
      ;;
  esac
done

if [ "$RUN_SECURE" = true ]; then
  : "${DEMO_USER_ID:?DEMO_USER_ID is required for secure acceptance}"
  : "${DEMO_USER_PASSWORD:?DEMO_USER_PASSWORD is required for secure acceptance}"
  : "${DEMO_ADMIN_USER_ID:?DEMO_ADMIN_USER_ID is required for secure acceptance}"
  : "${DEMO_ADMIN_PASSWORD:?DEMO_ADMIN_PASSWORD is required for secure acceptance}"
  : "${ADMIN_TOKEN:?ADMIN_TOKEN is required for secure acceptance}"
  : "${GRAFANA_ADMIN_USER:?GRAFANA_ADMIN_USER is required for secure acceptance}"
  : "${GRAFANA_ADMIN_PASSWORD:?GRAFANA_ADMIN_PASSWORD is required for secure acceptance}"
  export APP_AUTH_DEV_USERS="${APP_AUTH_DEV_USERS:-${DEMO_USER_ID}:${DEMO_USER_PASSWORD},${DEMO_ADMIN_USER_ID}:${DEMO_ADMIN_PASSWORD}}"
  export ADMIN_DEV_TOKEN="${ADMIN_DEV_TOKEN:-$ADMIN_TOKEN}"
fi

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
# shellcheck source=lib/health-poll.sh
source "$ROOT/scripts/lib/health-poll.sh"

RESULTS=()
record() {
  local name="$1" status="$2"
  RESULTS+=("$status  $name")
  echo ""
  echo ">>> [$status] $name"
  echo ""
}

fail_gate() {
  local name="$1"
  record "$name" "FAIL"
  echo "================================================================="
  echo "  ACCEPTANCE FAILED"
  echo "================================================================="
  for line in "${RESULTS[@]}"; do
    echo "  $line"
  done
  exit 1
}

pass_gate() {
  record "$1" "PASS"
}

echo "================================================================="
echo "  big-market acceptance (${MODE})"
echo "  $(date)"
echo "================================================================="
echo ""

# ── Fresh: destroy volumes ────────────────────────────────────────────────────

if [ "$MODE" = "fresh" ]; then
  if [ "$CONFIRM_DESTROY" != true ]; then
    echo "ERROR: --fresh requires --confirm-destroy-volumes (destructive)." >&2
    exit 1
  fi
  echo "Stopping stacks and removing volumes..."
  docker compose down -v 2>/dev/null || true
  docker compose -f docs/dev-ops/docker-compose-environment.yml down -v 2>/dev/null || true
fi

# ── Build ─────────────────────────────────────────────────────────────────────

if [ "$SKIP_BUILD" = false ]; then
  echo "Gate: mvn verify"
  if ! mvn verify -DfailIfNoTests=false; then
    fail_gate "mvn verify"
  fi
  pass_gate "mvn verify"
else
  pass_gate "mvn verify (skipped)"
fi

# ── Docker up ─────────────────────────────────────────────────────────────────

echo "Gate: infrastructure + application"
if ! docker compose -f docs/dev-ops/docker-compose-environment.yml up -d; then
  fail_gate "infra docker up"
fi
if ! ./scripts/apply-stack-migrations.sh; then
  fail_gate "apply-stack-migrations"
fi

COMPOSE_FILES=(-f docker-compose.yml)
if [ "$RUN_SECURE" = true ]; then
  COMPOSE_FILES+=(-f docker-compose.secure.yml)
fi
if ! docker compose "${COMPOSE_FILES[@]}" up --build -d; then
  fail_gate "app docker up"
fi

if ! wait_for_stack_healthy "$HOST" 240; then
  fail_gate "stack health"
fi
pass_gate "stack health"

# ── Ensure demo activity ──────────────────────────────────────────────────────

echo "Gate: ensure demo activity online"
ENSURE_OK=false
for _attempt in 1 2 3; do
  if ./scripts/ensure-demo-activity-online.sh "http://${HOST}:8080"; then
    ENSURE_OK=true
    break
  fi
  sleep 3
done
if [ "$ENSURE_OK" != true ]; then
  fail_gate "ensure-demo-activity-online"
fi
pass_gate "ensure-demo-activity-online"

# Allow Nacos/chatbot config propagation after admin save.
sleep 3

# ── Smoke ─────────────────────────────────────────────────────────────────────

echo "Gate: test-http-contracts"
if ! ./scripts/test-http-contracts.sh; then
  fail_gate "test-http-contracts"
fi
pass_gate "test-http-contracts"

echo "Gate: smoke-test-microservices"
if ! ./scripts/smoke-test-microservices.sh "$HOST"; then
  fail_gate "smoke-test-microservices"
fi
pass_gate "smoke-test-microservices"

echo "Gate: smoke-api"
if ! ./scripts/smoke-api.sh; then
  fail_gate "smoke-api"
fi
pass_gate "smoke-api"

# ── Chat refund E2E ───────────────────────────────────────────────────────────

echo "Gate: xxl-job-admin health"
if ! wait_for_xxl_admin "$HOST" 120; then
  fail_gate "xxl-job-admin health"
fi
pass_gate "xxl-job-admin health"

echo "Gate: smoke-chat-refund-e2e"
if ! ./scripts/smoke-chat-refund-e2e.sh; then
  fail_gate "smoke-chat-refund-e2e"
fi
pass_gate "smoke-chat-refund-e2e"

# ── Security (optional) ───────────────────────────────────────────────────────

if [ "$SKIP_SECURITY" = false ]; then
  echo "Gate: smoke-security"
  if [ -x ./scripts/smoke-security.sh ]; then
    if ! ./scripts/smoke-security.sh; then
      fail_gate "smoke-security"
    fi
    pass_gate "smoke-security"
  else
    fail_gate "smoke-security (script missing)"
  fi
fi

# ── Playwright ────────────────────────────────────────────────────────────────

if [ "$SKIP_PLAYWRIGHT" = false ]; then
  echo "Gate: Playwright (double run)"
  ./scripts/web-start.sh
  echo "Gate: reset demo chatbot config (post E2E)"
  if ! ./scripts/ensure-demo-activity-online.sh "http://${HOST}:8080"; then
    fail_gate "ensure-demo-activity-online (pre-playwright)"
  fi
  if ! npx playwright test --workers=1; then
    fail_gate "playwright run 1"
  fi
  if ! npx playwright test --workers=1; then
    fail_gate "playwright run 2"
  fi
  pass_gate "playwright (2x)"
else
  pass_gate "playwright (skipped)"
fi

echo ""
echo "================================================================="
echo "  ACCEPTANCE PASSED"
echo "================================================================="
for line in "${RESULTS[@]}"; do
  echo "  $line"
done
echo ""

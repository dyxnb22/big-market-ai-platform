#!/usr/bin/env bash
# Acceptance gates: build → (optional stack start) → migrate → smoke →
# chat refund E2E → Playwright. Fail-closed.
#
# Docker is NEVER started unless --start-stack is passed.
# Start the stack yourself first, or use --start-stack for CI bootstrap.
#
# Usage:
#   ./scripts/acceptance.sh                         # --reuse: require healthy stack
#   ./scripts/acceptance.sh --reuse                  # old volumes OK; no compose up
#   ./scripts/acceptance.sh --fresh --confirm-destroy-volumes --start-stack
#   ./scripts/acceptance.sh --secure --start-stack   # non-default credentials
#   ./scripts/acceptance.sh --skip-build --skip-playwright
#
# Modes:
#   --reuse   Prove compatibility with existing volumes (default).
#   --fresh   Destroy volumes first; proves full init (needs --confirm-destroy-volumes).
#   --secure  Run smoke-security; requires DEMO_* / ADMIN_TOKEN / GRAFANA_* env.
#
# --fresh alone does not start containers; add --start-stack to rebuild after destroy.

set -euo pipefail

MODE="reuse"
SKIP_BUILD=false
SKIP_PLAYWRIGHT=false
SKIP_SECURITY=true
CONFIRM_DESTROY=false
START_STACK=false
HOST="${HOST:-localhost}"
RUN_SECURE=false
ACCEPTANCE_START_EPOCH="$(date +%s)"
GIT_SHA="$(git rev-parse --short HEAD 2>/dev/null || echo unknown)"

for arg in "$@"; do
  case "$arg" in
    --fresh) MODE="fresh" ;;
    --reuse) MODE="reuse" ;;
    --skip-build) SKIP_BUILD=true ;;
    --skip-playwright) SKIP_PLAYWRIGHT=true ;;
    --secure) RUN_SECURE=true; SKIP_SECURITY=false ;;
    --confirm-destroy-volumes) CONFIRM_DESTROY=true ;;
    --start-stack) START_STACK=true ;;
    --help|-h)
      sed -n '2,22p' "$0"
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

ARTIFACT_DIR="${ROOT}/target/acceptance-artifacts"
mkdir -p "$ARTIFACT_DIR"

RESULTS=()
GATE_TIMINGS=()
CURRENT_GATE=""
GATE_START=0

record() {
  local name="$1" status="$2"
  RESULTS+=("$status  $name")
  echo ""
  echo ">>> [$status] $name"
  echo ""
}

print_manual_start_hint() {
  cat <<EOF
Stack is not healthy and --start-stack was not set (no auto-start).

Start manually, then re-run acceptance:

  docker compose -f docs/dev-ops/docker-compose-environment.yml up -d
  ./scripts/apply-stack-migrations.sh
  docker compose up --build -d
  # secure overlay:
  # docker compose -f docker-compose.yml -f docker-compose.secure.yml up --build -d

Or bootstrap via script:

  ./scripts/acceptance.sh --${MODE}$([ "$MODE" = "fresh" ] && echo " --confirm-destroy-volumes" || true) --start-stack
EOF
}

save_failure_artifacts() {
  local reason="${1:-unknown}"
  local stamp
  stamp="$(date +%Y%m%d-%H%M%S)"
  local dir="${ARTIFACT_DIR}/${stamp}-${reason//[^a-zA-Z0-9_-]/_}"
  mkdir -p "$dir"
  {
    echo "git_sha=${GIT_SHA}"
    echo "mode=${MODE}"
    echo "secure=${RUN_SECURE}"
    echo "start_stack=${START_STACK}"
    echo "host=${HOST}"
    echo "failed_gate=${reason}"
    echo "date=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  } >"${dir}/meta.txt"

  docker compose ps >"${dir}/compose-ps.txt" 2>&1 || true
  docker compose -f docs/dev-ops/docker-compose-environment.yml ps >"${dir}/infra-ps.txt" 2>&1 || true

  local svc
  for svc in big-market-gateway big-market-auth-service big-market-admin-service \
    big-market-market-service big-market-message-job-service big-market-chatbot-service \
    big-market-account-service; do
    docker compose logs --tail=200 "$svc" >"${dir}/${svc}.log" 2>&1 || true
  done

  {
    echo "=== actuator health snapshot ==="
    local port
    for port in 8080 8081 8082 8083 8084 8085 8086; do
      echo "--- :${port} ---"
      curl -sf --max-time 2 "http://${HOST}:${port}/actuator/health" || echo "(unreachable)"
      echo ""
    done
  } >"${dir}/health-snapshot.txt" 2>&1 || true

  echo "Failure artifacts saved to: ${dir}"
}

print_summary() {
  local outcome="$1"
  local elapsed=$(( $(date +%s) - ACCEPTANCE_START_EPOCH ))
  echo ""
  echo "================================================================="
  echo "  ACCEPTANCE ${outcome}"
  echo "================================================================="
  echo "  commit:      ${GIT_SHA}"
  echo "  mode:        ${MODE}"
  echo "  secure:      ${RUN_SECURE}"
  echo "  start_stack: ${START_STACK}"
  echo "  elapsed_s:   ${elapsed}"
  echo "  host:        ${HOST}"
  echo "-----------------------------------------------------------------"
  for line in "${RESULTS[@]}"; do
    echo "  $line"
  done
  if [ "${#GATE_TIMINGS[@]}" -gt 0 ]; then
    echo "-----------------------------------------------------------------"
    echo "  gate timings (s):"
    for t in "${GATE_TIMINGS[@]}"; do
      echo "    $t"
    done
  fi
  echo ""
  {
    echo "outcome=${outcome}"
    echo "git_sha=${GIT_SHA}"
    echo "mode=${MODE}"
    echo "secure=${RUN_SECURE}"
    echo "start_stack=${START_STACK}"
    echo "elapsed_s=${elapsed}"
    echo "host=${HOST}"
    printf '%s\n' "${RESULTS[@]}"
  } >"${ARTIFACT_DIR}/latest-summary.txt"
}

fail_gate() {
  local name="$1"
  if [ -n "$CURRENT_GATE" ] && [ "$GATE_START" -gt 0 ]; then
    GATE_TIMINGS+=("${CURRENT_GATE}=$(( $(date +%s) - GATE_START ))")
  fi
  record "$name" "FAIL"
  save_failure_artifacts "$name"
  print_summary "FAILED"
  exit 1
}

pass_gate() {
  local name="$1"
  if [ -n "$CURRENT_GATE" ] && [ "$GATE_START" -gt 0 ]; then
    GATE_TIMINGS+=("${CURRENT_GATE}=$(( $(date +%s) - GATE_START ))")
  fi
  record "$name" "PASS"
  CURRENT_GATE=""
  GATE_START=0
}

begin_gate() {
  CURRENT_GATE="$1"
  GATE_START="$(date +%s)"
  echo "Gate: $1"
}

count_playwright_tests() {
  # Playwright 1.54 prints relative spec names (without the tests/e2e prefix)
  # and finishes with: "Total: N tests in M files".
  npx playwright test --list 2>/dev/null | awk '/^Total: [0-9]+ tests? / { print $2; found=1 } END { if (!found) print 0 }'
}

assert_playwright_artifacts() {
  if [ ! -d "${ROOT}/playwright-report" ] && [ ! -d "${ROOT}/test-results" ]; then
    echo "ERROR: Playwright produced no report under playwright-report/ or test-results/" >&2
    return 1
  fi
}

echo "================================================================="
echo "  big-market acceptance (${MODE})"
echo "  commit=${GIT_SHA}  start_stack=${START_STACK}  secure=${RUN_SECURE}"
echo "  $(date)"
echo "================================================================="
echo ""

# ── Fresh: destroy volumes (does not start stack) ─────────────────────────────

if [ "$MODE" = "fresh" ]; then
  if [ "$CONFIRM_DESTROY" != true ]; then
    echo "ERROR: --fresh requires --confirm-destroy-volumes (destructive)." >&2
    exit 1
  fi
  begin_gate "destroy volumes"
  echo "Stopping stacks and removing volumes..."
  docker compose down -v 2>/dev/null || true
  docker compose -f docs/dev-ops/docker-compose-environment.yml down -v 2>/dev/null || true
  pass_gate "destroy volumes"
fi

# ── Build ─────────────────────────────────────────────────────────────────────

if [ "$SKIP_BUILD" = false ]; then
  begin_gate "mvn verify"
  if ! mvn verify -DfailIfNoTests=false; then
    fail_gate "mvn verify"
  fi
  pass_gate "mvn verify"
else
  pass_gate "mvn verify (skipped)"
fi

# ── Docker: optional start, always require health ──────────────────────────────

COMPOSE_FILES=(-f docker-compose.yml)
if [ "$RUN_SECURE" = true ]; then
  COMPOSE_FILES+=(-f docker-compose.secure.yml)
fi

if [ "$START_STACK" = true ]; then
  begin_gate "start stack"
  if ! docker compose -f docs/dev-ops/docker-compose-environment.yml up -d; then
    fail_gate "infra docker up"
  fi
  if ! ./scripts/apply-stack-migrations.sh; then
    fail_gate "apply-stack-migrations"
  fi
  if ! docker compose "${COMPOSE_FILES[@]}" up --build -d; then
    fail_gate "app docker up"
  fi
  pass_gate "start stack"
else
  echo "Gate: start stack (skipped — no --start-stack; will only health-check)"
  # Still apply migrations if MySQL is already up (idempotent; fail soft if down).
  if docker exec mysql mysqladmin ping -uroot -p123456 --silent 2>/dev/null; then
    begin_gate "apply-stack-migrations"
    if ! ./scripts/apply-stack-migrations.sh; then
      fail_gate "apply-stack-migrations"
    fi
    pass_gate "apply-stack-migrations"
  else
    pass_gate "apply-stack-migrations (skipped — mysql not reachable)"
  fi
fi

begin_gate "stack health"
# Fast preflight when not starting stack: fail in seconds if gateway is down.
if [ "$START_STACK" != true ]; then
  if ! curl -sf --max-time 3 "http://${HOST}:8080/actuator/health" >/dev/null 2>&1; then
    print_manual_start_hint
    fail_gate "stack health (gateway unreachable; no --start-stack)"
  fi
fi
if ! wait_for_stack_healthy "$HOST" 240; then
  if [ "$START_STACK" != true ]; then
    print_manual_start_hint
  fi
  fail_gate "stack health"
fi
pass_gate "stack health"

# ── Ensure demo activity ──────────────────────────────────────────────────────

begin_gate "ensure-demo-activity-online"
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

begin_gate "test-http-contracts"
if ! ./scripts/test-http-contracts.sh; then
  fail_gate "test-http-contracts"
fi
pass_gate "test-http-contracts"

begin_gate "smoke-test-microservices"
if ! ./scripts/smoke-test-microservices.sh "$HOST"; then
  fail_gate "smoke-test-microservices"
fi
pass_gate "smoke-test-microservices"

begin_gate "smoke-api"
if ! ./scripts/smoke-api.sh; then
  fail_gate "smoke-api"
fi
pass_gate "smoke-api"

# ── Chat refund E2E ───────────────────────────────────────────────────────────

begin_gate "xxl-job-admin health"
if ! wait_for_xxl_admin "$HOST" 120; then
  fail_gate "xxl-job-admin health"
fi
pass_gate "xxl-job-admin health"

begin_gate "xxl executor registration"
if ! wait_for_xxl_executor "big-market-message-job" 120; then
  fail_gate "xxl executor registration"
fi
pass_gate "xxl executor registration"

begin_gate "smoke-raffle-award-e2e"
if ! ./scripts/smoke-raffle-award-e2e.sh; then
  fail_gate "smoke-raffle-award-e2e"
fi
pass_gate "smoke-raffle-award-e2e"

begin_gate "smoke-chat-refund-e2e"
if ! ./scripts/smoke-chat-refund-e2e.sh; then
  fail_gate "smoke-chat-refund-e2e"
fi
pass_gate "smoke-chat-refund-e2e"

# ── Security (optional) ───────────────────────────────────────────────────────

if [ "$SKIP_SECURITY" = false ]; then
  begin_gate "smoke-security"
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
  begin_gate "playwright"
  ./scripts/web-start.sh
  if ! ./scripts/ensure-demo-activity-online.sh "http://${HOST}:8080"; then
    fail_gate "ensure-demo-activity-online (pre-playwright)"
  fi
  if [ -z "${PLAYWRIGHT_BROWSERS_PATH:-}" ] && [ -d "${ROOT}/.playwright-browsers" ]; then
    export PLAYWRIGHT_BROWSERS_PATH="${ROOT}/.playwright-browsers"
    echo "Using repository-local Playwright browser cache: ${PLAYWRIGHT_BROWSERS_PATH}"
  fi
  local_listed="$(count_playwright_tests)"
  if [ "${local_listed:-0}" -lt 1 ]; then
    fail_gate "playwright (zero tests listed)"
  fi
  echo "Playwright listed ${local_listed} e2e entries; refusing silent skip."
  if ! npx playwright test --workers=1; then
    fail_gate "playwright run 1"
  fi
  if ! npx playwright test --workers=1; then
    fail_gate "playwright run 2"
  fi
  if ! assert_playwright_artifacts; then
    fail_gate "playwright (missing report)"
  fi
  pass_gate "playwright (2x, ${local_listed} listed)"
else
  pass_gate "playwright (skipped)"
fi

print_summary "PASSED"
exit 0

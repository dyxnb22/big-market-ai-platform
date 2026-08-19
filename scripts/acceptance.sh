#!/usr/bin/env bash
# 验收门禁：构建 →（可选）启动服务栈 → 迁移 → 冒烟测试 →
# 聊天退款 E2E → Playwright；任一门禁失败即终止。
#
# 除非传入 --start-stack，否则绝不启动 Docker。
# 请先自行启动服务栈，或使用 --start-stack 完成 CI 引导启动。
#
# 用法：
#   ./scripts/acceptance.sh                         # --reuse：要求服务栈健康
#   ./scripts/acceptance.sh --reuse                  # 允许旧卷；不执行 compose up
#   ./scripts/acceptance.sh --fresh --confirm-destroy-volumes --start-stack
#   ./scripts/acceptance.sh --secure --start-stack   # 使用非默认凭据
#   ./scripts/acceptance.sh --skip-build --skip-playwright
#
# 模式：
#   --reuse   验证与现有卷的兼容性（默认）。
#   --fresh   先销毁卷，验证完整初始化（需要 --confirm-destroy-volumes）。
#   --secure  执行 smoke-security；需要 DEMO_* / ADMIN_TOKEN / GRAFANA_* 环境变量。
#
# 单独使用 --fresh 不会启动容器；销毁后请追加 --start-stack 重新构建并启动。

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
  : "${MYSQL_USER:?MYSQL_USER is required for secure acceptance}"
  : "${MYSQL_ROOT_PASSWORD:?MYSQL_ROOT_PASSWORD is required for secure acceptance}"
  : "${RABBITMQ_USER:?RABBITMQ_USER is required for secure acceptance}"
  : "${RABBITMQ_PASS:?RABBITMQ_PASS is required for secure acceptance}"
  : "${XXL_JOB_ADMIN_USER:?XXL_JOB_ADMIN_USER is required for secure acceptance}"
  : "${XXL_JOB_ADMIN_PASSWORD:?XXL_JOB_ADMIN_PASSWORD is required for secure acceptance}"
  export APP_AUTH_DEV_USERS="${APP_AUTH_DEV_USERS:-${DEMO_USER_ID}:${DEMO_USER_PASSWORD},${DEMO_ADMIN_USER_ID}:${DEMO_ADMIN_PASSWORD}}"
  export ADMIN_DEV_TOKEN="${ADMIN_DEV_TOKEN:-$ADMIN_TOKEN}"
fi

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
# shellcheck source=lib/health-poll.sh
source "$ROOT/scripts/lib/health-poll.sh"
# shellcheck source=lib/java17-precheck.sh
source "$ROOT/scripts/lib/java17-precheck.sh"
require_java_17

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

  docker compose -f docs/dev-ops/docker-compose-environment.yml up -d mysql redis rabbitmq nacos xxl-job-admin elasticsearch
  ./scripts/apply-stack-migrations.sh
  docker compose up --build -d
  # secure 配置覆盖层：
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
  # Playwright 1.54 输出不含 tests/e2e 前缀的相对 spec 名称，
  # 并以 "Total: N tests in M files" 结束。
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

# ── Fresh：销毁卷（不启动服务栈） ─────────────────────────────

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

# ── 构建 ─────────────────────────────────────────────────────────────────────

if [ "$SKIP_BUILD" = false ]; then
  begin_gate "mvn verify"
  if ! mvn verify -DfailIfNoTests=false; then
    fail_gate "mvn verify"
  fi
  pass_gate "mvn verify"
else
  pass_gate "mvn verify (skipped)"
fi

# ── Docker：可选启动，但始终要求健康检查通过 ──────────────────────────────

COMPOSE_FILES=(-f docker-compose.yml)
if [ "$RUN_SECURE" = true ]; then
  COMPOSE_FILES+=(-f docker-compose.secure.yml)
fi

if [ "$START_STACK" = true ]; then
  begin_gate "start stack"
  if ! docker compose -f docs/dev-ops/docker-compose-environment.yml up -d mysql redis rabbitmq nacos xxl-job-admin elasticsearch; then
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
  # 如果 MySQL 已启动，仍执行迁移（可幂等；MySQL 未启动时软失败）。
  if docker exec mysql mysqladmin ping -uroot -p"${MYSQL_ROOT_PASSWORD:-123456}" --silent 2>/dev/null; then
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
# 不启动服务栈时执行快速预检：网关未启动则在数秒内失败。
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

# ── 确保演示活动 ──────────────────────────────────────────────────────

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

# admin 保存后，等待 Nacos/chatbot 配置传播完成。
sleep 3

# ── 冒烟测试 ─────────────────────────────────────────────────────────────────────

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

begin_gate "smoke-nacos-runtime-config"
if ! ./scripts/smoke-nacos-runtime-config.sh; then
  fail_gate "smoke-nacos-runtime-config"
fi
pass_gate "smoke-nacos-runtime-config"

# ── 聊天退款 E2E ───────────────────────────────────────────────────────────

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

# ── 安全测试（可选） ───────────────────────────────────────────────────────

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

# ── Playwright 测试 ────────────────────────────────────────────────────────────────

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

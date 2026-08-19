#!/usr/bin/env bash
# 仅检查仓库内容的运行时安全校验器。
#
# 警告：即使 Spring Context / Mapper / XXL 对齐仍有问题，本脚本也可能报告 PASS。
# 不要将它作为启动或闭环就绪的唯一门禁。
# 本脚本检查默认凭据、按 Profile 选择的账户路径、共享 Mapper 副本、学习 DDL 隔离，
# 以及安全加固类是否存在。
#
# 本校验器用于补充（不能替代）：
#   validate-microservices-stack.sh
#   smoke-test-microservices.sh
#   smoke-api.sh
#
# 结果确定、只检查仓库，不依赖 DB/MQ/Docker/网络。

set -u

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PASS=0
FAIL=0
MESSAGE_JOB_YML="$REPO_ROOT/big-market-message-job-service/src/main/resources/application.yml"

pass() { echo "[PASS] $*"; PASS=$((PASS + 1)); }
fail() { echo "[FAIL] $*"; FAIL=$((FAIL + 1)); }

echo ""
echo "========================================================================"
echo "  Runtime Safety Validator"
echo "  Repo: $REPO_ROOT"
echo "========================================================================"

# ── 辅助函数：检查文件是否存在 ────────────────────────────────────────────────
assert_file() {
  local label="$1" path="$2"
  if [[ -f "$path" ]]; then
    pass "$label"
  else
    fail "$label — missing: $path"
  fi
}

# ── 辅助函数：检查给定文件/目录中不存在指定模式 ────────────────────
assert_pattern_absent() {
  local label="$1" pattern="$2"
  shift 2
  local matches
  matches=$(grep -RInE "$pattern" "$@" 2>/dev/null | grep -v '/target/' || true)
  if [[ -z "$matches" ]]; then
    pass "$label"
  else
    fail "$label"
    printf '%s\n' "$matches" | sed 's#^#       #'
  fi
}

# ── 辅助函数：检查指定文件中存在指定模式 ───────────────────────
assert_pattern_present() {
  local label="$1" file="$2" pattern="$3"
  if grep -qE "$pattern" "$file" 2>/dev/null; then
    pass "$label"
  else
    fail "$label — not found in $(basename "$file")"
  fi
}

# 从仓库中使用简单缩进格式的 application.yml 文件读取标量，
# 并解析 ${ENV_VAR:true} 这样的 Spring 占位符默认值。
# 这里有意不把嵌套 YAML 当作扁平化的点号文本处理。
yaml_default_value() {
  local file="$1" property_path="$2"
  python3 - "$file" "$property_path" <<'PY'
import re
import sys

path, wanted = sys.argv[1], sys.argv[2]
keys = {}
with open(path, encoding="utf-8") as stream:
    for raw in stream:
        match = re.match(r"^(\s*)([A-Za-z0-9_.-]+):(?:\s*(.*?))?\s*$", raw.rstrip("\n"))
        if not match:
            continue
        indent, key, value = match.groups()
        level = len(indent.expandtabs(2)) // 2
        keys[level] = key
        for stale in [item for item in keys if item > level]:
            del keys[stale]
        current = ".".join(keys[item] for item in sorted(keys) if item <= level)
        if current != wanted:
            continue
        value = (value or "").strip().strip('"\'')
        placeholder = re.fullmatch(r"\$\{[^}:]+:(-?)([^}]*)}", value)
        if placeholder:
            value = placeholder.group(2)
        print(value.lower())
        sys.exit(0)
sys.exit(1)
PY
}

# ═══════════════════════════════════════════════════════════════════════════════
# 第 1 节：默认凭据面审计
# ═══════════════════════════════════════════════════════════════════════════════
echo ""
echo "── 1. Default credentials — non-dev config files ──"

# 可能被用作生产或预发布配置模板的文件。
# 排除 dev/docker Profile，因为这些 Profile 可以合法包含开发默认值。
NON_DEV_CONFIGS=()
while IFS= read -r f; do
  NON_DEV_CONFIGS+=("$f")
done < <(find "$REPO_ROOT" -path '*/src/main/resources/application-prod.yml' \
           -o -path '*/src/main/resources/application-test.yml' 2>/dev/null \
           | grep -v '/target/' \
           | sort)

# 同时审计每个微服务的 application.yml（默认 Profile）和 spring-config-token.xml，
# 检查其中是否存在生产环境危险的硬编码密钥。
ALL_SVC_YMLS=()
ALL_TOKEN_XMLS=()
while IFS= read -r f; do
  ALL_SVC_YMLS+=("$f")
done < <(find "$REPO_ROOT" -path '*/src/main/resources/application.yml' \
           ! -path '*/target/*' 2>/dev/null | sort)
while IFS= read -r f; do
  ALL_TOKEN_XMLS+=("$f")
done < <(find "$REPO_ROOT" -path '*/src/main/resources/spring/spring-config-token.xml' \
           ! -path '*/target/*' 2>/dev/null | sort)

# 1a. JWT 密钥默认值
assert_pattern_absent \
  "No 'change-me' JWT secret in non-dev configs" \
  'change-me' \
  "${NON_DEV_CONFIGS[@]:-${REPO_ROOT}/NONEXISTENT_FILE_SAFETY_GUARD}"

# 1b. XXL-Job 访问令牌
assert_pattern_absent \
  "No 'default_token' XXL-Job token in non-dev configs" \
  'default_token' \
  "${NON_DEV_CONFIGS[@]:-${REPO_ROOT}/NONEXISTENT_FILE_SAFETY_GUARD}"

# 1c. 网关服务间令牌
assert_pattern_absent \
  "No '6ec604541f8b1ce4a' gateway token in non-dev configs" \
  '6ec604541f8b1ce4a' \
  "${NON_DEV_CONFIGS[@]:-${REPO_ROOT}/NONEXISTENT_FILE_SAFETY_GUARD}"

# 1d. 非开发配置中的开发认证用户
assert_pattern_absent \
  "No 'admin:admin' credential in non-dev configs" \
  'admin:admin' \
  "${NON_DEV_CONFIGS[@]:-${REPO_ROOT}/NONEXISTENT_FILE_SAFETY_GUARD}"

# 1e. 非开发配置必须通过注入方式提供基础设施凭据。
assert_pattern_absent \
  "No root/123456 MySQL credential in non-dev configs" \
  'username:[[:space:]]*root|password:[[:space:]]*123456' \
  "${NON_DEV_CONFIGS[@]:-${REPO_ROOT}/NONEXISTENT_FILE_SAFETY_GUARD}"

assert_pattern_absent \
  "No admin/admin RabbitMQ credential in non-dev configs" \
  'username:[[:space:]]*admin|password:[[:space:]]*admin' \
  "${NON_DEV_CONFIGS[@]:-${REPO_ROOT}/NONEXISTENT_FILE_SAFETY_GUARD}"

assert_pattern_absent \
  "No known hardcoded infra password in non-dev configs" \
  '12qw!@QW|admin-test-token' \
  "${NON_DEV_CONFIGS[@]:-${REPO_ROOT}/NONEXISTENT_FILE_SAFETY_GUARD}"

if [[ ${#NON_DEV_CONFIGS[@]} -gt 0 ]]; then
  for f in "${NON_DEV_CONFIGS[@]}"; do
    assert_pattern_present "RabbitMQ username env-injected in $(basename "$f")" "$f" 'username:[[:space:]]*\$\{RABBITMQ_USER\}'
    assert_pattern_present "RabbitMQ password env-injected in $(basename "$f")" "$f" 'password:[[:space:]]*\$\{RABBITMQ_PASS\}'
    assert_pattern_present "MySQL username env-injected in $(basename "$f")" "$f" 'username:[[:space:]]*\$\{MYSQL_USER\}'
    assert_pattern_present "MySQL password env-injected in $(basename "$f")" "$f" 'password:[[:space:]]*\$\{MYSQL_PASS\}'
  done
else
  pass "No non-dev config templates to audit"
fi


# 1f. 微服务 application.yml 不得直接使用未通过环境变量注入的 change-me-in-dev-only
#     作为默认值；应使用 ${ENV_VAR:change-me-in-dev-only}（带开发回退值的环境变量注入）。
#     未使用环境变量包装的硬编码 'change-me-in-dev-only' 属于风险信号。
if [[ ${#ALL_SVC_YMLS[@]} -gt 0 ]]; then
  # 确保每个服务的 yml 使用环境变量替换 JWT 密钥，而不是直接写入字面量。
  for f in "${ALL_SVC_YMLS[@]}"; do
    short="${f##*/big-market-ai-platform/}"
    if grep -qE 'secret:[[:space:]]*change-me-in-dev-only' "$f" 2>/dev/null; then
      fail "Bare JWT secret default (no env-var wrapper) in $short"
    elif grep -qE 'secret:[[:space:]]*\$\{[A-Z_]+(:.*)?}' "$f" 2>/dev/null; then
      pass "JWT secret uses env-var injection in $short"
    fi
  done
fi

# 1g. spring-config-token.xml 不得包含硬编码的 Dubbo 令牌字面量。
#     令牌应通过 ${DUBBO_APP_TOKEN:...} 注入。
if [[ ${#ALL_TOKEN_XMLS[@]} -gt 0 ]]; then
  assert_pattern_absent \
    "No bare hardcoded Dubbo app token in spring-config-token.xml files" \
    'value="89iu7o8732ijd9114"' \
    "${ALL_TOKEN_XMLS[@]}"
fi

echo ""
echo "── 1.2 Default credentials — docker-compose dev annotations ──"

COMPOSE_FILES=("$REPO_ROOT/docker-compose.yml" "$REPO_ROOT/docs/dev-ops/docker-compose-app.yml")
for f in "${COMPOSE_FILES[@]}"; do
  [[ -f "$f" ]] || continue
  if grep -q 'change-me-in-docker-dev-only' "$f" 2>/dev/null; then
    pass "docker-compose JWT default is dev-annotated: $(basename "$f")"
  elif grep -qE 'JWT_SECRET.*change-me-in-prod' "$f" 2>/dev/null; then
    fail "docker-compose JWT default still says 'change-me-in-prod': $(basename "$f")"
  else
    pass "docker-compose JWT default not 'change-me-in-prod': $(basename "$f")"
  fi
done

echo ""
echo "── 1.2.1 Docker stack enables shared Redis token revocation ──"

COMPOSE_MAIN="$REPO_ROOT/docker-compose.yml"
if [[ -f "$COMPOSE_MAIN" ]]; then
  for svc in big-market-auth-service big-market-admin-service big-market-market-service; do
    if awk -v svc="$svc" '$0 ~ "^  "svc":" {found=1} found && /TOKEN_REVOCATION_REDIS_ENABLED=true/ {exit 0} found && /^  [a-z]/ && $0 !~ "^  "svc":" {exit 1}' "$COMPOSE_MAIN"; then
      pass "docker-compose enables Redis token revocation: $svc"
    else
      fail "docker-compose missing TOKEN_REVOCATION_REDIS_ENABLED=true for $svc"
    fi
  done
fi

echo ""
echo "── 1.3 DefaultCredentialGuard class presence ──"

# 防护类已移动到 big-market-domain，以便扫描 com.dyx.market.domain.auth 的所有服务
#（market-service、admin-service、auth-service）自动发现它。
GUARD_JAVA="$REPO_ROOT/big-market-domain/src/main/java/com/dyx/market/domain/auth/service/DefaultCredentialGuard.java"
assert_file "DefaultCredentialGuard.java exists in domain" "$GUARD_JAVA"
assert_pattern_present "DefaultCredentialGuard is an InitializingBean" "$GUARD_JAVA" "implements InitializingBean"
assert_pattern_present "DefaultCredentialGuard has guardEnabled flag" "$GUARD_JAVA" 'default-credential-guard\.enabled'
assert_pattern_present "DefaultCredentialGuard detects dev profiles" "$GUARD_JAVA" 'DEV_PROFILES'
assert_pattern_present "DefaultCredentialGuard checks Dubbo app token" "$GUARD_JAVA" '89iu7o8732ijd9114'

# ═══════════════════════════════════════════════════════════════════════════════
# 第 2 节：按 Profile 选择的账户路径与固定奖品 Outbox
# ═══════════════════════════════════════════════════════════════════════════════
echo ""
echo "── 2.1 Final topology: local strategy/rebate providers ──"

MARKET_YML="$REPO_ROOT/big-market-market-service/src/main/resources/application.yml"

assert_pattern_present "Local rebate order adapter remains" \
  "$REPO_ROOT/big-market-trigger/src/main/java/com/dyx/market/trigger/adapter/LocalRebateOrderAdapter.java" \
  'class LocalRebateOrderAdapter'
assert_pattern_present "Local rebate read adapter remains" \
  "$REPO_ROOT/big-market-trigger/src/main/java/com/dyx/market/trigger/adapter/LocalRebateReadAdapter.java" \
  'class LocalRebateReadAdapter'
assert_pattern_present "Local strategy read adapter remains" \
  "$REPO_ROOT/big-market-trigger/src/main/java/com/dyx/market/trigger/adapter/LocalStrategyReadAdapter.java" \
  'class LocalStrategyReadAdapter'
echo ""
echo "── 2.2 Profile-selected account routing ──"

assert_pattern_present "Remote account reads are shared" \
  "$REPO_ROOT/big-market-trigger/src/main/java/com/dyx/market/trigger/adapter/RemoteAccountReadAdapter.java" \
  'class RemoteAccountReadAdapter'
assert_pattern_present "Remote account reads fail closed" \
  "$REPO_ROOT/big-market-trigger/src/main/java/com/dyx/market/trigger/adapter/RemoteAccountReadAdapter.java" \
  'RemoteAccountReadAdapter.*account-service|account-service 账户读取失败'
assert_pattern_present "Remote activity account port is Docker-profiled" \
  "$REPO_ROOT/big-market-trigger/src/main/java/com/dyx/market/trigger/account/RemoteActivityAccountPort.java" \
  '@Profile\("docker"\)'
assert_pattern_present "Local activity account port is local-profiled" \
  "$REPO_ROOT/big-market-infrastructure/src/main/java/com/dyx/market/infrastructure/adapter/port/LocalActivityAccountPort.java" \
  '@Profile\(\{"dev", "local", "test"\}\)'
assert_pattern_absent \
  "No runtime account routing flags remain" \
  'ACCOUNT_SERVICE_REMOTE_(READ|CREDIT_WRITE|QUOTA_WRITE|QUOTA_DECREMENT)|account\.service\.remote-(read|credit-write|quota-write|quota-decrement)\.enabled' \
  "$REPO_ROOT/big-market-market-service" "$REPO_ROOT/big-market-message-job-service" \
  "$REPO_ROOT/big-market-trigger" "$REPO_ROOT/big-market-infrastructure" \
  "$REPO_ROOT/big-market-domain" "$REPO_ROOT/docker-compose.yml" "$REPO_ROOT/docs"

echo ""
echo "── 2.3 Fixed credit award Outbox ──"

AWARD_SUPPORT="$REPO_ROOT/big-market-infrastructure/src/main/java/com/dyx/market/infrastructure/adapter/repository/AwardCreditGrantSupport.java"
AWARD_JOB="$REPO_ROOT/big-market-message-job-service/src/main/java/com/dyx/market/message/job/config/DispatchCreditAwardTaskJob.java"
PROJECT_DIRS=("$REPO_ROOT"/big-market-* "$REPO_ROOT/docs")
assert_pattern_present "AwardRepository always writes credit outbox" "$AWARD_SUPPORT" 'saveWithCreditOutbox'
assert_pattern_absent "Direct award credit write path removed" 'saveWithDirectCredit|awardCreditOutboxEnabled|account\.award-credit-outbox\.enabled' "$AWARD_SUPPORT"
assert_pattern_absent "Credit award dispatcher has no feature switch" 'ConditionalOnProperty|account\.award-credit-outbox\.enabled' "$AWARD_JOB"
assert_pattern_present "Outbox schema guard remains" "$REPO_ROOT/big-market-message-job-service/src/main/java/com/dyx/market/message/job/config/OutboxSchemaValidator.java" 'credit_award_task_000'
assert_pattern_absent "Obsolete shared-task credit flags removed" 'ACCOUNT_AWARD_CREDIT_OUTBOX|JOB_SHARED_TASK_DISPATCH_CREDIT_AWARD|shared-task-dispatch|job-mutual-exclusion-guard|flag-mutual-exclusion-guard' "$REPO_ROOT/docker-compose.yml" "${PROJECT_DIRS[@]}"

# ═══════════════════════════════════════════════════════════════════════════════
# 第 3 节：令牌撤销服务存在性
# ═══════════════════════════════════════════════════════════════════════════════
echo ""
echo "── 3. Token revocation infrastructure ──"

REVOKE_IFACE="$REPO_ROOT/big-market-domain/src/main/java/com/dyx/market/domain/auth/service/ITokenRevocationService.java"
REVOKE_IMPL="$REPO_ROOT/big-market-domain/src/main/java/com/dyx/market/domain/auth/service/InMemoryTokenRevocationService.java"
REVOKE_CONFIG="$REPO_ROOT/big-market-domain/src/main/java/com/dyx/market/domain/auth/config/TokenRevocationConfig.java"
REVOKE_REDIS="$REPO_ROOT/big-market-domain/src/main/java/com/dyx/market/domain/auth/service/RedisTokenRevocationService.java"
JWT_UTILS="$REPO_ROOT/big-market-domain/src/main/java/com/dyx/market/domain/auth/util/JwtTokenUtils.java"
REVOKE_TEST="$REPO_ROOT/big-market-domain/src/test/java/com/dyx/market/domain/auth/service/TokenRevocationServiceTest.java"

assert_file "ITokenRevocationService.java exists" "$REVOKE_IFACE"
assert_pattern_present "ITokenRevocationService declares revoke()" "$REVOKE_IFACE" 'void revoke'
assert_pattern_present "ITokenRevocationService declares isRevoked()" "$REVOKE_IFACE" 'boolean isRevoked'

assert_file "InMemoryTokenRevocationService.java exists" "$REVOKE_IMPL"
assert_pattern_present "InMemoryTokenRevocationService implements interface" "$REVOKE_IMPL" 'implements ITokenRevocationService'
assert_pattern_present "InMemoryTokenRevocationService has eviction" "$REVOKE_IMPL" 'evictExpired|evict'

assert_file "TokenRevocationConfig.java exists" "$REVOKE_CONFIG"
assert_pattern_present "TokenRevocationConfig is @Configuration" "$REVOKE_CONFIG" '@Configuration'
assert_pattern_present "TokenRevocationConfig creates ITokenRevocationService bean" "$REVOKE_CONFIG" 'ITokenRevocationService'
assert_pattern_present "TokenRevocationConfig fail-fast when Redis enabled without Redisson" "$REVOKE_CONFIG" 'IllegalStateException'

assert_file "RedisTokenRevocationService.java exists (optional)" "$REVOKE_REDIS"
assert_pattern_present "RedisTokenRevocationService implements interface" "$REVOKE_REDIS" 'implements ITokenRevocationService'
assert_pattern_present "RedisTokenRevocationService fails closed on Redis read errors" "$REVOKE_REDIS" 'return true'
assert_pattern_present "RedisTokenRevocationService throws on Redis revoke failure" "$REVOKE_REDIS" 'Failed to revoke token in Redis'

assert_file "JwtTokenUtils.java exists" "$JWT_UTILS"
assert_pattern_present "JwtTokenUtils strips Bearer prefix" "$JWT_UTILS" 'BEARER_PREFIX'

assert_file "TokenRevocationServiceTest.java exists" "$REVOKE_TEST"

echo ""
echo "── 3.2 AuthAccessController logout endpoint ──"

AUTH_CTRL="$REPO_ROOT/big-market-auth-service/src/main/java/com/dyx/market/auth/AuthAccessController.java"
assert_file "AuthAccessController.java exists" "$AUTH_CTRL"
assert_pattern_present "AuthAccessController has logout endpoint" "$AUTH_CTRL" '@RequestMapping.*logout|@PostMapping.*logout'
assert_pattern_present "AuthAccessController injects ITokenRevocationService" "$AUTH_CTRL" 'ITokenRevocationService'
assert_pattern_present "AuthAccessController calls extractJti" "$AUTH_CTRL" 'extractJti'

echo ""
echo "── 3.3 IAuthService JTI extraction ──"

AUTH_IFACE="$REPO_ROOT/big-market-domain/src/main/java/com/dyx/market/domain/auth/service/IAuthService.java"
assert_file "IAuthService.java exists" "$AUTH_IFACE"
assert_pattern_present "IAuthService declares extractJti" "$AUTH_IFACE" 'String extractJti'

AUTH_ABSTRACT="$REPO_ROOT/big-market-domain/src/main/java/com/dyx/market/domain/auth/service/AbstractAuthService.java"
assert_pattern_present "AbstractAuthService implements extractJtiFromToken" "$AUTH_ABSTRACT" 'extractJtiFromToken'

AUTH_SVC="$REPO_ROOT/big-market-domain/src/main/java/com/dyx/market/domain/auth/service/AuthService.java"
assert_pattern_present "AuthService.checkToken consults revocation service" "$AUTH_SVC" 'tokenRevocationService'

# ═══════════════════════════════════════════════════════════════════════════════
# 第 4 节：网关限流器存在性
# ═══════════════════════════════════════════════════════════════════════════════
echo ""
echo "── 4. Gateway rate limiter ──"

RATE_LIMITER="$REPO_ROOT/big-market-gateway/src/main/java/com/dyx/market/gateway/config/RateLimiterConfig.java"
assert_file "RateLimiterConfig.java exists" "$RATE_LIMITER"
assert_pattern_present "RateLimiterConfig is @Configuration" "$RATE_LIMITER" '@Configuration'
assert_pattern_present "RateLimiterConfig has IpPathRateLimit factory" "$RATE_LIMITER" 'IpPathRateLimitGatewayFilterFactory'
assert_pattern_present "RateLimiterConfig uses token bucket" "$RATE_LIMITER" 'TokenBucket|token.*bucket|tryConsume'

GATEWAY_YML="$REPO_ROOT/big-market-gateway/src/main/resources/application.yml"
GATEWAY_DOCKER_YML="$REPO_ROOT/big-market-gateway/src/main/resources/application-docker.yml"
assert_pattern_present "Gateway base yml defines active profile" "$GATEWAY_YML" 'profiles:'
assert_pattern_present "Gateway docker yml references IpPathRateLimit" "$GATEWAY_DOCKER_YML" 'IpPathRateLimit'

# ═══════════════════════════════════════════════════════════════════════════════
# 第 5 节：服务 Mapper 副本存在性
# ═══════════════════════════════════════════════════════════════════════════════
echo ""
echo "── 5. Service mapper copies still present ──"

# 本地学习模式使用的共享 Mapper 副本。
check_mapper_copy() {
  local label="$1" relpath="$2"
  assert_file "$label present" "$REPO_ROOT/$relpath"
}

check_mapper_copy "big-market-market-service task_mapper.xml" "big-market-market-service/src/main/resources/mybatis/mapper/mysql/task_mapper.xml"
check_mapper_copy "big-market-message-job-service task_mapper.xml" "big-market-message-job-service/src/main/resources/mybatis/mapper/mysql/task_mapper.xml"
check_mapper_copy "big-market-account-service raffle_activity_account_mapper.xml" "big-market-account-service/src/main/resources/mybatis/mapper/mysql/raffle_activity_account_mapper.xml"
check_mapper_copy "big-market-account-service task_mapper.xml" "big-market-account-service/src/main/resources/mybatis/mapper/mysql/task_mapper.xml"

# ═══════════════════════════════════════════════════════════════════════════════
# 第 6 节：学习 DDL 位置
# ═══════════════════════════════════════════════════════════════════════════════
echo ""
echo "── 6. Learning DDL stays under docs/sql/*.sql ──"

# 本节专门检查 docs/sql 学习参考文件之外是否存在看起来可执行的 DDL 语句。
DDL_VIOLATIONS=$(grep -RInE '\b(CREATE|ALTER|DROP)[[:space:]]+(TABLE|INDEX|DATABASE)\b' \
  "$REPO_ROOT/docs" --include='*.sql' 2>/dev/null \
  | grep -v '/docs/sql/' \
  | grep -v '/docs/dev-ops/' \
  || true)

if [[ -z "$DDL_VIOLATIONS" ]]; then
  pass "No DDL outside docs/sql learning references"
else
  fail "DDL statements found outside docs/sql learning references:"
  printf '%s\n' "$DDL_VIOLATIONS"
fi

# 同时验证已知的 5 个学习 DDL 文件存在。
LEARNING_DDL_FILES=(
  "docs/sql/award-dispatch-task-outbox.sql"
  "docs/sql/credit-award-task-outbox.sql"
  "docs/sql/credit-trade-task-outbox.sql"
  "docs/sql/quota-decrement-ledger.sql"
  "docs/sql/rebate-task-outbox.sql"
)

for ddl in "${LEARNING_DDL_FILES[@]}"; do
  assert_file "Learning DDL present: $ddl" "$REPO_ROOT/$ddl"
done

# ═══════════════════════════════════════════════════════════════════════════════
# 第 7 节：交叉验证同级校验器
# ═══════════════════════════════════════════════════════════════════════════════
echo ""
echo "── 7. Sibling validators ──"

SIBLING_VALIDATORS=(
  "scripts/validate-microservices-stack.sh"
  "scripts/smoke-test-microservices.sh"
  "scripts/smoke-api.sh"
)

for sib in "${SIBLING_VALIDATORS[@]}"; do
  if [[ -x "$REPO_ROOT/$sib" ]]; then
    pass "Sibling validator executable: $sib"
  else
    fail "Sibling validator missing or not executable: $sib"
  fi
done

# 同时验证 MICROSERVICES.md 是权威入口文档。
MICROSERVICES_MD="$REPO_ROOT/docs/MICROSERVICES.md"
assert_file "MICROSERVICES.md is authoritative entry point" "$MICROSERVICES_MD"
assert_pattern_present "MICROSERVICES.md declares itself authoritative" "$MICROSERVICES_MD" 'authoritative entry point'

# ═══════════════════════════════════════════════════════════════════════════════
# 第 8 节：XXL 处理器 ↔ 种子对齐 + market 扫描排除（启动 P0）
# ═══════════════════════════════════════════════════════════════════════════════
echo ""
echo "── 8. XXL @XxlJob handlers ⊆ xxl_job.sql seeds; market excludes job/listener ──"

XXL_SQL="$REPO_ROOT/docs/dev-ops/mysql/sql/xxl_job.sql"
MARKET_APP="$REPO_ROOT/big-market-market-service/src/main/java/com/dyx/market/market/MarketServiceApplication.java"

if [[ -f "$XXL_SQL" ]]; then
  SEEDED_HANDLERS=$(grep -E "^\s*\([0-9]+,1," "$XXL_SQL" \
    | grep -oE "'(updateAwardStockJob|SendMessageTaskJob_DB[12]|UpdateActivitySkuStockJob|DispatchCreditAwardTaskJob_DB[12]|StrategyAwardStockConfirmJob_DB[12]|CreditPayDeliveryReconcileJob_DB[12]|RemoteWriteReconcileJob|DlqReplayJob|ChatRefundReconcileJob|ChatDeductReconcileJob)'" \
    | tr -d "'" \
    | sort -u)
  while IFS= read -r handler; do
    [[ -z "$handler" ]] && continue
    if printf '%s\n' "$SEEDED_HANDLERS" | grep -qx "$handler"; then
      pass "@XxlJob(\"$handler\") seeded in xxl_job.sql"
    else
      fail "@XxlJob(\"$handler\") missing from xxl_job.sql seeds"
    fi
  done < <(grep -RhoE '^[[:space:]]*@XxlJob\("[^"]+"\)' \
      "$REPO_ROOT/big-market-trigger" \
      "$REPO_ROOT/big-market-message-job-service" \
      --include='*.java' 2>/dev/null \
    | sed -E 's/.*@XxlJob\("([^"]+)"\).*/\1/' \
    | sort -u)

  if [[ "${COMPOSE_OUTBOX_ENABLED:-0}" -eq 1 ]]; then
    for handler in DispatchCreditAwardTaskJob_DB1 DispatchCreditAwardTaskJob_DB2; do
      if grep -E "'$handler'.*,'',1,0,0\)[,;]?$" "$XXL_SQL" >/dev/null; then
        pass "$handler seed runs with Docker's default award-credit outbox"
      else
        fail "$handler seed must run with Docker's default award-credit outbox"
      fi
    done
  fi
  if [[ "$(yaml_default_value "$MESSAGE_JOB_YML" "job.dlq-replay.enabled" 2>/dev/null)" == "false" ]]; then
    if grep -E "'DlqReplayJob'.*,'',0,0,0\)[,;]?$" "$XXL_SQL" >/dev/null; then
      pass "DlqReplayJob seed is stopped while reviewed replay defaults off"
    else
      fail "DlqReplayJob seed must be stopped while reviewed replay defaults off"
    fi
  else
    fail "DLQ replay must default off pending explicit idempotency review"
  fi
else
  fail "xxl_job.sql missing"
fi

if [[ -f "$MARKET_APP" ]]; then
  if grep -qE 'com\.dyx\.market\.trigger\.(job|listener)' "$MARKET_APP"; then
    fail "market-service must not scan trigger.job / trigger.listener"
  else
    pass "market-service scanBasePackages excludes trigger.job / trigger.listener"
  fi
  if grep -q 'com.dyx.market.trigger.http' "$MARKET_APP"; then
    pass "market-service scans trigger.http"
  else
    fail "market-service missing trigger.http scan"
  fi
else
  fail "MarketServiceApplication.java missing"
fi

# ═══════════════════════════════════════════════════════════════════════════════
# 第 9 节：当前文档和脚本的最终命名护栏
# ═══════════════════════════════════════════════════════════════════════════════
echo ""
echo "── 9. Final naming guardrail for docs and scripts ──"

FINAL_STATE_FORBIDDEN_PATTERN="$(IFS='|'; echo \
  "P""hase" \
  "dark[ -]launch" \
  "scaf""fold" \
  "EXTERNAL-G""ATED" \
  "repo-re""ady" \
  "cut""over" \
  "leg""acy" \
  "proposed-on""ly")"

FINAL_STATE_MATCHES=$(grep -RInE "$FINAL_STATE_FORBIDDEN_PATTERN" \
  "$REPO_ROOT/README.md" "$REPO_ROOT/docs" "$REPO_ROOT/scripts" \
  --include='*.md' --include='*.sh' 2>/dev/null \
  | grep -v '/docs/dev-ops/' \
  || true)

if [[ -z "$FINAL_STATE_MATCHES" ]]; then
  pass "Current docs and scripts use final architecture naming"
else
  fail "Current docs/scripts contain old architecture naming:"
  printf '%s\n' "$FINAL_STATE_MATCHES" | sed 's#^#       #'
fi

# ═══════════════════════════════════════════════════════════════════════════════
# 汇总
# ═══════════════════════════════════════════════════════════════════════════════
echo ""
echo "========================================================================"
echo "  SUMMARY"
echo "========================================================================"
echo "Checks passed: $PASS"
echo "Checks failed: $FAIL"
echo ""

if [[ "$FAIL" -eq 0 ]]; then
  echo "RESULT: ALL CHECKS PASSED — runtime safety guardrails intact"
  echo "        Default credential guards, mutual-exclusion validators,"
  echo "        token revocation service, gateway rate limiter, shared mapper"
  echo "        copies, and learning DDL isolation are all in place."
  exit 0
else
  echo "RESULT: $FAIL CHECK(S) FAILED — review output above"
  exit 1
fi

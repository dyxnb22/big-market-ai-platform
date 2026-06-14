#!/usr/bin/env bash
# Repo-only runtime safety validator.
#
# Validates final-architecture guardrails and verifies that no regression has
# occurred in default credentials, mutually exclusive flag paths,
# shared mapper copies, learning DDL isolation, or the presence of safety
# hardening classes.
#
# This validator complements (does not replace):
#   validate-microservices-stack.sh
#   smoke-test-microservices.sh
#   smoke-api.sh
#
# Deterministic, repo-only, no DB/MQ/Docker/network.

set -u

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PASS=0
FAIL=0

pass() { echo "[PASS] $*"; PASS=$((PASS + 1)); }
fail() { echo "[FAIL] $*"; FAIL=$((FAIL + 1)); }

echo ""
echo "========================================================================"
echo "  Runtime Safety Validator"
echo "  Repo: $REPO_ROOT"
echo "========================================================================"

# ── Helper: check a file exists ────────────────────────────────────────────────
assert_file() {
  local label="$1" path="$2"
  if [[ -f "$path" ]]; then
    pass "$label"
  else
    fail "$label — missing: $path"
  fi
}

# ── Helper: check pattern is absent across given files/dirs ────────────────────
assert_pattern_absent() {
  local label="$1" pattern="$2"
  shift 2
  local matches
  matches=$(grep -RInE "$pattern" "$@" 2>/dev/null | grep -v '/target/' | grep -v '/docs/archive/' || true)
  if [[ -z "$matches" ]]; then
    pass "$label"
  else
    fail "$label"
    printf '%s\n' "$matches" | sed 's#^#       #'
  fi
}

# ── Helper: check pattern is present in a specific file ───────────────────────
assert_pattern_present() {
  local label="$1" file="$2" pattern="$3"
  if grep -qE "$pattern" "$file" 2>/dev/null; then
    pass "$label"
  else
    fail "$label — not found in $(basename "$file")"
  fi
}

# ═══════════════════════════════════════════════════════════════════════════════
# Section 1: Default credential surface audit
# ═══════════════════════════════════════════════════════════════════════════════
echo ""
echo "── 1. Default credentials — non-dev config files ──"

# Files that could be used as production or staging config templates.
# Dev/docker profiles are excluded — those may legitimately carry dev defaults.
NON_DEV_CONFIGS=()
for f in \
  "$REPO_ROOT/big-market-app/src/main/resources/application-prod.yml" \
  "$REPO_ROOT/big-market-app/src/main/resources/application-test.yml"; do
  [[ -f "$f" ]] && NON_DEV_CONFIGS+=("$f")
done

# Also audit every microservice application.yml (default profile) and
# spring-config-token.xml for hardcoded secrets that would be dangerous in prod.
ALL_SVC_YMLS=()
ALL_TOKEN_XMLS=()
while IFS= read -r f; do
  ALL_SVC_YMLS+=("$f")
done < <(find "$REPO_ROOT" -path '*/src/main/resources/application.yml' \
           ! -path '*/target/*' ! -path '*/big-market-app/*' 2>/dev/null | sort)
while IFS= read -r f; do
  ALL_TOKEN_XMLS+=("$f")
done < <(find "$REPO_ROOT" -path '*/src/main/resources/spring/spring-config-token.xml' \
           ! -path '*/target/*' 2>/dev/null | sort)

# 1a. JWT secret defaults
assert_pattern_absent \
  "No 'change-me' JWT secret in non-dev configs" \
  'change-me' \
  "${NON_DEV_CONFIGS[@]:-${REPO_ROOT}/NONEXISTENT_FILE_SAFETY_GUARD}"

# 1b. XXL-Job access token
assert_pattern_absent \
  "No 'default_token' XXL-Job token in non-dev configs" \
  'default_token' \
  "${NON_DEV_CONFIGS[@]:-${REPO_ROOT}/NONEXISTENT_FILE_SAFETY_GUARD}"

# 1c. Gateway inter-service token
assert_pattern_absent \
  "No '6ec604541f8b1ce4a' gateway token in non-dev configs" \
  '6ec604541f8b1ce4a' \
  "${NON_DEV_CONFIGS[@]:-${REPO_ROOT}/NONEXISTENT_FILE_SAFETY_GUARD}"

# 1d. Dev auth users in non-dev configs
assert_pattern_absent \
  "No 'admin:admin' credential in non-dev configs" \
  'admin:admin' \
  "${NON_DEV_CONFIGS[@]:-${REPO_ROOT}/NONEXISTENT_FILE_SAFETY_GUARD}"

# 1e. Infrastructure credentials must be injected in non-dev configs.
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

for f in "${NON_DEV_CONFIGS[@]}"; do
  assert_pattern_present "RabbitMQ username env-injected in $(basename "$f")" "$f" 'username:[[:space:]]*\$\{RABBITMQ_USER\}'
  assert_pattern_present "RabbitMQ password env-injected in $(basename "$f")" "$f" 'password:[[:space:]]*\$\{RABBITMQ_PASS\}'
  assert_pattern_present "MySQL username env-injected in $(basename "$f")" "$f" 'username:[[:space:]]*\$\{MYSQL_USER\}'
  assert_pattern_present "MySQL password env-injected in $(basename "$f")" "$f" 'password:[[:space:]]*\$\{MYSQL_PASS\}'
done


# 1f. Microservice application.yml files must not carry bare change-me-in-dev-only
#     as an uncommitted default; they should use ${ENV_VAR:change-me-in-dev-only}
#     (env-var injection with dev fallback) which is acceptable, but a literal
#     hardcoded 'change-me-in-dev-only' without an env-var wrapper is a red flag.
if [[ ${#ALL_SVC_YMLS[@]} -gt 0 ]]; then
  # Ensure each svc yml uses env-var substitution for the JWT secret, not a bare literal.
  for f in "${ALL_SVC_YMLS[@]}"; do
    short="${f##*/big-market-ai-platform/}"
    if grep -qE 'secret:[[:space:]]*change-me-in-dev-only' "$f" 2>/dev/null; then
      fail "Bare JWT secret default (no env-var wrapper) in $short"
    elif grep -qE 'secret:[[:space:]]*\$\{[A-Z_]+(:.*)?}' "$f" 2>/dev/null; then
      pass "JWT secret uses env-var injection in $short"
    fi
  done
fi

# 1g. spring-config-token.xml must not contain the literal hardcoded Dubbo token.
#     The token should be injected with ${DUBBO_APP_TOKEN:...}.
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
echo "── 1.3 DefaultCredentialGuard class presence ──"

# Guard was moved to big-market-domain so it is auto-discovered by all services
# that scan com.dyx.market.domain.auth (market-service, admin-service, auth-service).
GUARD_JAVA="$REPO_ROOT/big-market-domain/src/main/java/com/dyx/market/domain/auth/service/DefaultCredentialGuard.java"
assert_file "DefaultCredentialGuard.java exists in domain" "$GUARD_JAVA"
assert_pattern_present "DefaultCredentialGuard is an InitializingBean" "$GUARD_JAVA" "implements InitializingBean"
assert_pattern_present "DefaultCredentialGuard has guardEnabled flag" "$GUARD_JAVA" 'default-credential-guard\.enabled'
assert_pattern_present "DefaultCredentialGuard detects dev profiles" "$GUARD_JAVA" 'DEV_PROFILES'
assert_pattern_present "DefaultCredentialGuard checks Dubbo app token" "$GUARD_JAVA" '89iu7o8732ijd9114'

# ═══════════════════════════════════════════════════════════════════════════════
# Section 2: Flag mutual-exclusion guardrails (config-file defaults)
# ═══════════════════════════════════════════════════════════════════════════════
echo ""
echo "── 2.1 Mutual-exclusion: embedded provider vs service provider ──"

# Rebate: embedded provider must NOT be default-true WHILE service create is default-true
REBATE_EMBEDDED_DEFAULT_TRUE=0
REBATE_REMOTE_DEFAULT_TRUE=0
for dir in "$REPO_ROOT"/big-market-market-service/src/main/resources \
           "$REPO_ROOT"/big-market-app/src/main/resources; do
  [[ -d "$dir" ]] || continue
  if grep -RqE 'REBATE_EMBEDDED_RPC_PROVIDER_ENABLED:-true|rebate\.embedded-rpc-provider\.enabled.*:.*true' "$dir" 2>/dev/null; then
    REBATE_EMBEDDED_DEFAULT_TRUE=1
  fi
  if grep -RqE 'REBATE_SERVICE_REMOTE_CREATE_ORDER_ENABLED:-true|rebate\.service\.remote-create-order\.enabled.*:.*true' "$dir" 2>/dev/null; then
    REBATE_REMOTE_DEFAULT_TRUE=1
  fi
done
if [[ "$REBATE_EMBEDDED_DEFAULT_TRUE" -eq 1 && "$REBATE_REMOTE_DEFAULT_TRUE" -eq 1 ]]; then
  fail "Rebate embedded provider AND remote create-order both default to true — dual-provider risk"
else
  pass "Rebate dual-provider config: embedded=$REBATE_EMBEDDED_DEFAULT_TRUE remote=$REBATE_REMOTE_DEFAULT_TRUE (not both true)"
fi

# Strategy: embedded provider must NOT be default-true WHILE remote read is default-true
STRATEGY_EMBEDDED_DEFAULT_TRUE=0
STRATEGY_REMOTE_DEFAULT_TRUE=0
for dir in "$REPO_ROOT"/big-market-market-service/src/main/resources \
           "$REPO_ROOT"/big-market-app/src/main/resources; do
  [[ -d "$dir" ]] || continue
  if grep -RqE 'STRATEGY_EMBEDDED_RPC_PROVIDER_ENABLED:-true|strategy\.embedded-rpc-provider\.enabled.*:.*true' "$dir" 2>/dev/null; then
    STRATEGY_EMBEDDED_DEFAULT_TRUE=1
  fi
  if grep -RqE 'STRATEGY_SERVICE_REMOTE_READ_ENABLED:-true|strategy\.service\.remote-read\.enabled.*:.*true' "$dir" 2>/dev/null; then
    STRATEGY_REMOTE_DEFAULT_TRUE=1
  fi
done
if [[ "$STRATEGY_EMBEDDED_DEFAULT_TRUE" -eq 1 && "$STRATEGY_REMOTE_DEFAULT_TRUE" -eq 1 ]]; then
  fail "Strategy embedded provider AND remote read both default to true — dual-provider risk"
else
  pass "Strategy dual-provider config: embedded=$STRATEGY_EMBEDDED_DEFAULT_TRUE remote=$STRATEGY_REMOTE_DEFAULT_TRUE (not both true)"
fi

echo ""
echo "── 2.2 Mutual-exclusion: shared task dispatch vs per-domain outbox ──"

# Shared task dispatch (SendMessageTaskJob) must NOT be active while
# per-domain outbox dispatchers (DispatchCreditAwardTaskJob) are enabled.
MESSAGE_JOB_YML="$REPO_ROOT/big-market-message-job-service/src/main/resources/application.yml"
if [[ -f "$MESSAGE_JOB_YML" ]]; then
  if grep -qE 'ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED:-true|account\.award-credit-outbox\.enabled.*:.*true' "$MESSAGE_JOB_YML" 2>/dev/null; then
    OUTBOX_ENABLED=1
  else
    OUTBOX_ENABLED=0
  fi
  if grep -qE 'job\.shared-task-dispatch\.credit-award-disabled.*:.*true' "$MESSAGE_JOB_YML" 2>/dev/null; then
    SHARED_TASK_DISABLED=1
  else
    SHARED_TASK_DISABLED=0
  fi
  if [[ "$OUTBOX_ENABLED" -gt 0 && "$SHARED_TASK_DISABLED" -eq 0 ]]; then
    fail "message-job outbox enabled but shared-task-dispatch.credit-award-disabled is not true — dual-dispatch risk"
  else
    pass "message-job outbox+shared-task config: outbox_enabled_default=$OUTBOX_ENABLED shared_task_disabled=$SHARED_TASK_DISABLED (safe)"
  fi
else
  pass "message-job config absent (skip)"
fi

echo ""
echo "── 2.3 Mutual-exclusion validator classes present ──"

FLAG_VALIDATOR="$REPO_ROOT/big-market-market-service/src/main/java/com/dyx/market/market/config/FlagMutualExclusionValidator.java"
JOB_VALIDATOR="$REPO_ROOT/big-market-message-job-service/src/main/java/com/dyx/market/message/job/config/JobMutualExclusionValidator.java"

assert_file "FlagMutualExclusionValidator.java exists" "$FLAG_VALIDATOR"
assert_pattern_present "FlagMutualExclusionValidator checks rebate dual-path" "$FLAG_VALIDATOR" 'rebateRemoteCreateOrder.*rebateDefaultProvider|rebate.*duplicate'
assert_pattern_present "FlagMutualExclusionValidator checks strategy dual-path" "$FLAG_VALIDATOR" 'strategyRemoteRead.*strategyDefaultProvider|strategy.*duplicate'
assert_pattern_present "FlagMutualExclusionValidator has disable flag" "$FLAG_VALIDATOR" 'FLAG_MUTUAL_EXCLUSION_GUARD_ENABLED'

assert_file "JobMutualExclusionValidator.java exists" "$JOB_VALIDATOR"
assert_pattern_present "JobMutualExclusionValidator checks outbox vs shared-task" "$JOB_VALIDATOR" 'awardCreditOutboxEnabled.*sharedTaskCreditAwardDisabled|credit-award.*both'
assert_pattern_present "JobMutualExclusionValidator has disable flag" "$JOB_VALIDATOR" 'JOB_MUTUAL_EXCLUSION_GUARD_ENABLED'

# ═══════════════════════════════════════════════════════════════════════════════
# Section 3: Token revocation service presence
# ═══════════════════════════════════════════════════════════════════════════════
echo ""
echo "── 3. Token revocation infrastructure ──"

REVOKE_IFACE="$REPO_ROOT/big-market-domain/src/main/java/com/dyx/market/domain/auth/service/ITokenRevocationService.java"
REVOKE_IMPL="$REPO_ROOT/big-market-domain/src/main/java/com/dyx/market/domain/auth/service/InMemoryTokenRevocationService.java"
REVOKE_CONFIG="$REPO_ROOT/big-market-auth-service/src/main/java/com/dyx/market/auth/service/config/TokenRevocationConfig.java"
REVOKE_REDIS="$REPO_ROOT/big-market-auth-service/src/main/java/com/dyx/market/auth/service/config/RedisTokenRevocationService.java"
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

assert_file "RedisTokenRevocationService.java exists (optional)" "$REVOKE_REDIS"
assert_pattern_present "RedisTokenRevocationService implements interface" "$REVOKE_REDIS" 'implements ITokenRevocationService'

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
# Section 4: Gateway rate limiter presence
# ═══════════════════════════════════════════════════════════════════════════════
echo ""
echo "── 4. Gateway rate limiter ──"

RATE_LIMITER="$REPO_ROOT/big-market-gateway/src/main/java/com/dyx/market/gateway/config/RateLimiterConfig.java"
assert_file "RateLimiterConfig.java exists" "$RATE_LIMITER"
assert_pattern_present "RateLimiterConfig is @Configuration" "$RATE_LIMITER" '@Configuration'
assert_pattern_present "RateLimiterConfig has IpPathRateLimit factory" "$RATE_LIMITER" 'IpPathRateLimitGatewayFilterFactory'
assert_pattern_present "RateLimiterConfig uses token bucket" "$RATE_LIMITER" 'TokenBucket|token.*bucket|tryConsume'

GATEWAY_YML="$REPO_ROOT/big-market-gateway/src/main/resources/application.yml"
assert_pattern_present "Gateway yml references IpPathRateLimit" "$GATEWAY_YML" 'IpPathRateLimit'

# ═══════════════════════════════════════════════════════════════════════════════
# Section 5: Shared mapper copy presence
# ═══════════════════════════════════════════════════════════════════════════════
echo ""
echo "── 5. Shared mapper copies still present ──"

# Old-path cleanup inventory - shared mapper copies used by local learning modes.
check_mapper_copy() {
  local label="$1" relpath="$2"
  assert_file "$label present" "$REPO_ROOT/$relpath"
}

check_mapper_copy "big-market-app task_mapper.xml"            "big-market-app/src/main/resources/mybatis/mapper/mysql/task_mapper.xml"
check_mapper_copy "big-market-market-service task_mapper.xml" "big-market-market-service/src/main/resources/mybatis/mapper/mysql/task_mapper.xml"
check_mapper_copy "big-market-message-job-service task_mapper.xml" "big-market-message-job-service/src/main/resources/mybatis/mapper/mysql/task_mapper.xml"
check_mapper_copy "big-market-account-service raffle_activity_account_mapper.xml" "big-market-account-service/src/main/resources/mybatis/mapper/mysql/raffle_activity_account_mapper.xml"
check_mapper_copy "big-market-account-service task_mapper.xml" "big-market-account-service/src/main/resources/mybatis/mapper/mysql/task_mapper.xml"
check_mapper_copy "big-market-rebate-service task_mapper.xml" "big-market-rebate-service/src/main/resources/mybatis/mapper/mysql/task_mapper.xml"

# ═══════════════════════════════════════════════════════════════════════════════
# Section 6: Learning DDL location
# ═══════════════════════════════════════════════════════════════════════════════
echo ""
echo "── 6. Learning DDL stays under docs/sql/*.sql ──"

# This section checks specifically for executable DDL-looking statements outside
# docs/sql learning references and archive material.
DDL_VIOLATIONS=$(grep -RInE '\b(CREATE|ALTER|DROP)[[:space:]]+(TABLE|INDEX|DATABASE)\b' \
  "$REPO_ROOT/docs" --include='*.sql' 2>/dev/null \
  | grep -v '/docs/sql/' \
  | grep -v '/docs/archive/' \
  | grep -v '/docs/dev-ops/' \
  || true)

if [[ -z "$DDL_VIOLATIONS" ]]; then
  pass "No DDL outside docs/sql learning references (excluding archive)"
else
  fail "DDL statements found outside docs/sql learning references:"
  printf '%s\n' "$DDL_VIOLATIONS"
fi

# Also verify the 5 known learning DDL files exist.
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
# Section 7: Cross-reference to sibling validators
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

# Also verify MICROSERVICES.md is the authoritative entry point
MICROSERVICES_MD="$REPO_ROOT/docs/MICROSERVICES.md"
assert_file "MICROSERVICES.md is authoritative entry point" "$MICROSERVICES_MD"
assert_pattern_present "MICROSERVICES.md declares itself authoritative" "$MICROSERVICES_MD" 'authoritative entry point'

# ═══════════════════════════════════════════════════════════════════════════════
# Summary
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

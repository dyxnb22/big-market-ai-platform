#!/usr/bin/env bash
# Repo-only Phase 8 runtime safety validator.
#
# Validates guards that were deployed in phase-8-safety-hardening and verifies
# that no regression has occurred in default credentials, mutually exclusive
# flag paths, compatibility mapper copies, proposed DDL isolation, or the
# presence of safety hardening classes.
#
# This validator complements (does not replace):
#   validate-microservices-production-flag-matrix.sh
#   validate-microservices-phase-7-task-outbox-ownership.sh
#   validate-microservices-phase-7-task-outbox-proposed-ddl.sh
#   validate-microservices-legacy-cleanup-readiness.sh
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
echo "  Phase 8 Runtime Safety Validator"
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
echo "── 1.3 DefaultCredentialsGuard class presence ──"

GUARD_JAVA="$REPO_ROOT/big-market-auth-service/src/main/java/com/dyx/market/auth/service/config/DefaultCredentialGuard.java"
assert_file "DefaultCredentialGuard.java exists" "$GUARD_JAVA"
assert_pattern_present "DefaultCredentialGuard is a CommandLineRunner" "$GUARD_JAVA" "implements CommandLineRunner"
assert_pattern_present "DefaultCredentialGuard has guardEnabled flag" "$GUARD_JAVA" 'default-credential-guard\.enabled'
assert_pattern_present "DefaultCredentialGuard detects dev profiles" "$GUARD_JAVA" 'DEV_PROFILES'

# ═══════════════════════════════════════════════════════════════════════════════
# Section 2: Flag mutual-exclusion guardrails (config-file defaults)
# ═══════════════════════════════════════════════════════════════════════════════
echo ""
echo "── 2.1 Mutual-exclusion: legacy provider vs remote provider ──"

# Rebate: legacy provider must NOT be default-true WHILE remote create is default-true
REBATE_LEGACY_DEFAULT_TRUE=0
REBATE_REMOTE_DEFAULT_TRUE=0
for dir in "$REPO_ROOT"/big-market-market-service/src/main/resources \
           "$REPO_ROOT"/big-market-app/src/main/resources; do
  [[ -d "$dir" ]] || continue
  if grep -RqE 'REBATE_LEGACY_RPC_PROVIDER_ENABLED:-true|rebate\.legacy-rpc-provider\.enabled.*:.*true' "$dir" 2>/dev/null; then
    REBATE_LEGACY_DEFAULT_TRUE=1
  fi
  if grep -RqE 'REBATE_SERVICE_REMOTE_CREATE_ORDER_ENABLED:-true|rebate\.service\.remote-create-order\.enabled.*:.*true' "$dir" 2>/dev/null; then
    REBATE_REMOTE_DEFAULT_TRUE=1
  fi
done
if [[ "$REBATE_LEGACY_DEFAULT_TRUE" -eq 1 && "$REBATE_REMOTE_DEFAULT_TRUE" -eq 1 ]]; then
  fail "Rebate legacy provider AND remote create-order both default to true — dual-provider risk"
else
  pass "Rebate dual-provider config: legacy=$REBATE_LEGACY_DEFAULT_TRUE remote=$REBATE_REMOTE_DEFAULT_TRUE (not both true)"
fi

# Strategy: legacy provider must NOT be default-true WHILE remote read is default-true
STRATEGY_LEGACY_DEFAULT_TRUE=0
STRATEGY_REMOTE_DEFAULT_TRUE=0
for dir in "$REPO_ROOT"/big-market-market-service/src/main/resources \
           "$REPO_ROOT"/big-market-app/src/main/resources; do
  [[ -d "$dir" ]] || continue
  if grep -RqE 'STRATEGY_LEGACY_RPC_PROVIDER_ENABLED:-true|strategy\.legacy-rpc-provider\.enabled.*:.*true' "$dir" 2>/dev/null; then
    STRATEGY_LEGACY_DEFAULT_TRUE=1
  fi
  if grep -RqE 'STRATEGY_SERVICE_REMOTE_READ_ENABLED:-true|strategy\.service\.remote-read\.enabled.*:.*true' "$dir" 2>/dev/null; then
    STRATEGY_REMOTE_DEFAULT_TRUE=1
  fi
done
if [[ "$STRATEGY_LEGACY_DEFAULT_TRUE" -eq 1 && "$STRATEGY_REMOTE_DEFAULT_TRUE" -eq 1 ]]; then
  fail "Strategy legacy provider AND remote read both default to true — dual-provider risk"
else
  pass "Strategy dual-provider config: legacy=$STRATEGY_LEGACY_DEFAULT_TRUE remote=$STRATEGY_REMOTE_DEFAULT_TRUE (not both true)"
fi

echo ""
echo "── 2.2 Mutual-exclusion: shared task fallback vs per-domain outbox ──"

# Shared task fallback (SendMessageTaskJob) must NOT be active while
# per-domain outbox dispatchers (DispatchCreditAwardTaskJob) are enabled.
MESSAGE_JOB_YML="$REPO_ROOT/big-market-message-job-service/src/main/resources/application.yml"
if [[ -f "$MESSAGE_JOB_YML" ]]; then
  if grep -qE 'ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED:-true|account\.award-credit-outbox\.enabled.*:.*true' "$MESSAGE_JOB_YML" 2>/dev/null; then
    OUTBOX_ENABLED=1
  else
    OUTBOX_ENABLED=0
  fi
  if grep -qE 'job\.shared-task-fallback\.credit-award-disabled.*:.*true' "$MESSAGE_JOB_YML" 2>/dev/null; then
    SHARED_TASK_DISABLED=1
  else
    SHARED_TASK_DISABLED=0
  fi
  if [[ "$OUTBOX_ENABLED" -gt 0 && "$SHARED_TASK_DISABLED" -eq 0 ]]; then
    fail "message-job outbox enabled but shared-task-fallback.credit-award-disabled is not true — dual-dispatch risk"
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
assert_pattern_present "FlagMutualExclusionValidator checks rebate dual-path" "$FLAG_VALIDATOR" 'rebateServiceRemoteCreateOrder.*rebateLegacyProvider|rebate.*duplicate'
assert_pattern_present "FlagMutualExclusionValidator checks strategy dual-path" "$FLAG_VALIDATOR" 'strategyRemoteRead.*strategyLegacyProvider|strategy.*duplicate'
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

AUTH_CTRL="$REPO_ROOT/big-market-auth-access/src/main/java/com/dyx/market/auth/AuthAccessController.java"
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
# Section 5: Compatibility mapper copy presence
# ═══════════════════════════════════════════════════════════════════════════════
echo ""
echo "── 5. Compatibility mapper copies still present ──"

# From docs/microservices-legacy-cleanup-inventory.md — key mapper compatibility
# copies that must remain until their 30-day removal gates are satisfied.
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
# Section 6: Proposed DDL isolation
# ═══════════════════════════════════════════════════════════════════════════════
echo ""
echo "── 6. Proposed DDL stays under docs/sql/proposed-*.sql ──"

# This section complements validate-microservices-phase-7-task-outbox-proposed-ddl.sh
# by checking specifically for CREATE/ALTER/DROP TABLE outside proposed/ and archive/.
DDL_VIOLATIONS=$(grep -RInE '\b(CREATE|ALTER|DROP)[[:space:]]+(TABLE|INDEX|DATABASE)\b' \
  "$REPO_ROOT/docs" --include='*.sql' 2>/dev/null \
  | grep -v '/docs/sql/proposed-' \
  | grep -v '/docs/archive/' \
  | grep -v '/docs/dev-ops/' \
  || true)

if [[ -z "$DDL_VIOLATIONS" ]]; then
  pass "No DDL outside docs/sql/proposed-*.sql (excluding archive)"
else
  fail "DDL statements found outside docs/sql/proposed-*.sql:"
  printf '%s\n' "$DDL_VIOLATIONS"
fi

# Also verify the 5 known proposed DDL files exist
PROPOSED_DDL_COUNT=$(find "$REPO_ROOT/docs/sql" -name 'proposed-*.sql' -type f 2>/dev/null | wc -l | tr -d ' ')
if [[ "$PROPOSED_DDL_COUNT" -ge 5 ]]; then
  pass "$PROPOSED_DDL_COUNT proposed DDL files under docs/sql/ (expect >=5)"
else
  fail "Only $PROPOSED_DDL_COUNT proposed DDL files found (expect >=5)"
fi

# ═══════════════════════════════════════════════════════════════════════════════
# Section 7: Cross-reference to sibling validators
# ═══════════════════════════════════════════════════════════════════════════════
echo ""
echo "── 7. Sibling validators remain referenced ──"

SIBLING_VALIDATORS=(
  "scripts/validate-microservices-production-flag-matrix.sh"
  "scripts/validate-microservices-phase-7-task-outbox-proposed-ddl.sh"
  "scripts/validate-microservices-phase-7-task-outbox-ownership.sh"
  "scripts/validate-microservices-legacy-cleanup-readiness.sh"
  "scripts/validate-microservices-post-cutover-cleanup-gates.sh"
  "scripts/validate-microservices-service-module-ownership.sh"
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
assert_pattern_present "MICROSERVICES.md declares itself authoritative" "$MICROSERVICES_MD" 'sole authoritative entry point'

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
  echo "RESULT: ALL CHECKS PASSED — Phase 8 runtime safety guardrails intact"
  echo "        Default credential guards, mutual-exclusion validators,"
  echo "        token revocation service, gateway rate limiter, compatibility"
  echo "        mapper copies, and proposed DDL isolation are all in place."
  exit 0
else
  echo "RESULT: $FAIL CHECK(S) FAILED — review output above"
  exit 1
fi

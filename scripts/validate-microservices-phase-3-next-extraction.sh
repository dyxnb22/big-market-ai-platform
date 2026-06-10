#!/usr/bin/env bash
# validate-microservices-phase-3-next-extraction.sh
# Deterministic repo-only validation for the Phase 3 rebate-service boundary.

set -uo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PASS=0
FAIL=0

pass() { echo "[PASS] $1"; PASS=$((PASS + 1)); }
fail() { echo "[FAIL] $1"; FAIL=$((FAIL + 1)); }

check_file() {
  local label="$1" path="$2"
  if [ -f "$ROOT/$path" ]; then
    pass "$label: $path"
  else
    fail "$label: missing $path"
  fi
}

check_contains() {
  local label="$1" path="$2" pattern="$3"
  if [ ! -f "$ROOT/$path" ]; then
    fail "$label: file missing $path"
    return
  fi
  if grep -qE "$pattern" "$ROOT/$path"; then
    pass "$label"
  else
    fail "$label: pattern not found in $path: $pattern"
  fi
}

check_not_contains() {
  local label="$1" path="$2" pattern="$3"
  if [ ! -f "$ROOT/$path" ]; then
    fail "$label: file missing $path"
    return
  fi
  if grep -qE "$pattern" "$ROOT/$path"; then
    fail "$label: forbidden pattern found in $path: $pattern"
  else
    pass "$label"
  fi
}

echo ""
echo "========================================================================"
echo "  Phase 3 Rebate-Service Boundary Validator"
echo "  Repo: $ROOT"
echo "========================================================================"

echo ""
echo "-- [1] Module and Maven wiring"
check_file "P3-MOD-1 root pom" "pom.xml"
check_contains "P3-MOD-2 root module registered" "pom.xml" "<module>big-market-rebate-service</module>"
check_file "P3-MOD-3 rebate-service pom" "big-market-rebate-service/pom.xml"
check_contains "P3-MOD-4 finalName" "big-market-rebate-service/pom.xml" "<finalName>big-market-rebate-service</finalName>"
check_contains "P3-MOD-5 boot main class" "big-market-rebate-service/pom.xml" "com.dyx.market.rebate.RebateServiceApplication"
check_contains "P3-MOD-6 depends on API contract jar" "big-market-rebate-service/pom.xml" "<artifactId>big-market-api</artifactId>"
check_contains "P3-MOD-7 depends on domain jar" "big-market-rebate-service/pom.xml" "<artifactId>big-market-domain</artifactId>"
check_contains "P3-MOD-8 depends on infrastructure jar" "big-market-rebate-service/pom.xml" "<artifactId>big-market-infrastructure</artifactId>"
check_not_contains "P3-MOD-9 does not depend on trigger jar" "big-market-rebate-service/pom.xml" "<artifactId>big-market-trigger</artifactId>"

echo ""
echo "-- [2] Rebate provider boundary"
APP="big-market-rebate-service/src/main/java/com/dyx/market/rebate/RebateServiceApplication.java"
PROVIDER="big-market-rebate-service/src/main/java/com/dyx/market/rebate/provider/RebateServiceRPC.java"
check_file "P3-RPC-1 application class" "$APP"
check_file "P3-RPC-2 provider class" "$PROVIDER"
check_contains "P3-RPC-3 application enables Dubbo" "$APP" "@EnableDubbo"
check_contains "P3-RPC-4 scans own package" "$APP" "com.dyx.market.rebate"
check_contains "P3-RPC-5 scans rebate domain" "$APP" "com.dyx.market.domain.rebate"
check_contains "P3-RPC-6 scans infrastructure" "$APP" "com.dyx.market.infrastructure"
check_not_contains "P3-RPC-7 does not scan trigger packages" "$APP" "com.dyx.market.trigger"
check_contains "P3-RPC-8 provider implements IRebateService" "$PROVIDER" "implements IRebateService"
check_contains "P3-RPC-9 provider exports Dubbo service" "$PROVIDER" "@DubboService\\(version = \"1.0\"\\)"
check_contains "P3-RPC-10 provider delegates to domain service" "$PROVIDER" "IBehaviorRebateService"
check_not_contains "P3-RPC-11 provider does not import activity domain" "$PROVIDER" "domain.activity"
check_not_contains "P3-RPC-12 provider does not import account/credit domain" "$PROVIDER" "domain.credit|IAccount"
check_not_contains "P3-RPC-13 provider does not import award domain" "$PROVIDER" "domain.award"
check_not_contains "P3-RPC-14 provider does not import strategy domain" "$PROVIDER" "domain.strategy"

echo ""
echo "-- [3] Runtime config remains dark-launch only"
APP_YML="big-market-rebate-service/src/main/resources/application.yml"
check_file "P3-CFG-1 application.yml" "$APP_YML"
check_contains "P3-CFG-2 server port 8088" "$APP_YML" "port: \\$\\{SERVER_PORT:8088\\}"
check_contains "P3-CFG-3 service name" "$APP_YML" "name: big-market-rebate-service"
check_contains "P3-CFG-4 Dubbo app name" "$APP_YML" "name: big-market-rebate"
check_contains "P3-CFG-5 dedicated Dubbo port" "$APP_YML" "port: 20883"
check_contains "P3-CFG-6 provider scan only rebate provider" "$APP_YML" "base-packages: com.dyx.market.rebate.provider"
check_not_contains "P3-CFG-7 no remote-award flag introduced" "$APP_YML" "remote-award"
check_not_contains "P3-CFG-8 no outbox flag introduced" "$APP_YML" "award-credit-outbox"
check_not_contains "P3-CFG-9 no quota decrement flag introduced" "$APP_YML" "remote-quota-decrement"

echo ""
echo "-- [4] Rebate persistence resources"
check_file "P3-MAP-1 mybatis config" "big-market-rebate-service/src/main/resources/mybatis/config/mybatis-config.xml"
check_file "P3-MAP-2 daily behavior rebate mapper" "big-market-rebate-service/src/main/resources/mybatis/mapper/mysql/daily_behavior_rebate_mapper.xml"
check_file "P3-MAP-3 user behavior rebate order mapper" "big-market-rebate-service/src/main/resources/mybatis/mapper/mysql/user_behavior_rebate_order_mapper.xml"
check_file "P3-MAP-4 generic task mapper for rebate outbox publish" "big-market-rebate-service/src/main/resources/mybatis/mapper/mysql/task_mapper.xml"

echo ""
echo "-- [5] Existing Phase 2 dangerous flags remain false"
FLAG_FAIL=0
while IFS= read -r f; do
  if grep -qE "ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED:true|ACCOUNT_FULFILLMENT_REMOTE_AWARD_ENABLED:true|ACCOUNT_SERVICE_REMOTE_QUOTA_DECREMENT_ENABLED:true|account\.service\.remote-quota-decrement\.enabled[[:space:]]*=[[:space:]]*true" "$f" 2>/dev/null; then
    echo "  [DANGER] $f"
    FLAG_FAIL=$((FLAG_FAIL + 1))
  fi
done < <(find "$ROOT" -not -path "*/target/*" \( -name "*.yml" -o -name "*.properties" \) 2>/dev/null)
if [ "$FLAG_FAIL" -eq 0 ]; then
  pass "P3-FLAG-1 no dangerous Phase 2 flag is hardcoded true"
else
  fail "P3-FLAG-1 dangerous flag hardcoded true in $FLAG_FAIL file(s)"
fi

echo ""
echo "-- [6] Documentation and generated evidence safety"
DOC="docs/microservices-split-phase-3-next-extraction.md"
check_file "P3-DOC-1 assessment doc" "$DOC"
check_contains "P3-DOC-2 documents chosen target" "$DOC" "Target: rebate-service"
check_contains "P3-DOC-3 documents coupling points" "$DOC" "Remaining Monolith Coupling Points"
check_contains "P3-DOC-4 documents DB ownership" "$DOC" "Database and Table Ownership"
check_contains "P3-DOC-5 documents RPC gaps" "$DOC" "RPC and API Contract Gaps"
check_contains "P3-DOC-6 documents job ownership" "$DOC" "Job Ownership"
check_contains "P3-DOC-7 documents validation command" "$DOC" "validate-microservices-phase-3-next-extraction.sh"
if git -C "$ROOT" ls-files "docs/evidence/generated/*" | grep -q .; then
  fail "P3-DOC-8 generated evidence files are tracked"
else
  pass "P3-DOC-8 generated evidence files are not tracked"
fi

echo ""
echo "========================================================================"
echo "  SUMMARY"
echo "========================================================================"
echo "PASS: $PASS"
echo "FAIL: $FAIL"

if [ "$FAIL" -eq 0 ]; then
  echo "RESULT: PASS"
  exit 0
else
  echo "RESULT: FAIL"
  exit 1
fi

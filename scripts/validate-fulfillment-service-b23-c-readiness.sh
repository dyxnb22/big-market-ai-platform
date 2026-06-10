#!/usr/bin/env bash
# validate-fulfillment-service-b23-c-readiness.sh
# Phase 2.3-C: verify repo is ready for staging validation.
# Static only — no Docker, no DB, no network required. Safe to run locally.

set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"

PASS=0
FAIL=0

check() {
  local id="$1" desc="$2" result="$3"
  if [ "$result" = "ok" ]; then
    echo "[PASS] $id: $desc"
    PASS=$((PASS + 1))
  else
    echo "[FAIL] $id: $desc"
    FAIL=$((FAIL + 1))
  fi
}

echo "=== Phase 2.3-C Staging Readiness Validation ==="
echo ""

# ── Config Safety ────────────────────────────────────────────────────────────

# C1: account.award-credit-outbox.enabled is NOT true by default in any config
TRUE_OUTBOX=0
while IFS= read -r f; do
  if grep -qE "ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED:true" "$f" 2>/dev/null; then
    TRUE_OUTBOX=$((TRUE_OUTBOX + 1))
  fi
done < <(find "$ROOT" -not -path "*/target/*" \( -name "*.yml" -o -name "*.properties" \) 2>/dev/null)
[ "$TRUE_OUTBOX" -eq 0 ] \
  && check C1 "account.award-credit-outbox.enabled NOT true by default in any config" ok \
  || check C1 "account.award-credit-outbox.enabled NOT true by default in any config (found $TRUE_OUTBOX)" fail

# C2: account.fulfillment.remote-award.enabled is NOT true by default in any config
TRUE_AWARD=0
while IFS= read -r f; do
  if grep -qE "ACCOUNT_FULFILLMENT_REMOTE_AWARD_ENABLED:true" "$f" 2>/dev/null; then
    TRUE_AWARD=$((TRUE_AWARD + 1))
  fi
done < <(find "$ROOT" -not -path "*/target/*" \( -name "*.yml" -o -name "*.properties" \) 2>/dev/null)
[ "$TRUE_AWARD" -eq 0 ] \
  && check C2 "account.fulfillment.remote-award.enabled NOT true by default in any config" ok \
  || check C2 "account.fulfillment.remote-award.enabled NOT true by default in any config (found $TRUE_AWARD)" fail

# C3: account.service.remote-quota-decrement.enabled is NOT true by default in any config
TRUE_QUOTA=0
while IFS= read -r f; do
  if grep -qE "remote-quota-decrement.*enabled.*: true|REMOTE_QUOTA_DECREMENT.*:true" "$f" 2>/dev/null; then
    TRUE_QUOTA=$((TRUE_QUOTA + 1))
  fi
done < <(find "$ROOT" -not -path "*/target/*" \( -name "*.yml" -o -name "*.properties" \) 2>/dev/null)
[ "$TRUE_QUOTA" -eq 0 ] \
  && check C3 "account.service.remote-quota-decrement.enabled NOT true by default in any config" ok \
  || check C3 "account.service.remote-quota-decrement.enabled NOT true by default in any config (found $TRUE_QUOTA)" fail

# C4: fulfillment-service application.yml has award-credit-outbox.enabled defaulting to false
FULFILLMENT_YML="$ROOT/big-market-fulfillment-service/src/main/resources/application.yml"
if grep -q "award-credit-outbox" "$FULFILLMENT_YML" && \
   grep -A3 "award-credit-outbox" "$FULFILLMENT_YML" | grep -q "ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED:false"; then
  check C4 "fulfillment-service application.yml has award-credit-outbox.enabled defaulting to false" ok
else
  check C4 "fulfillment-service application.yml has award-credit-outbox.enabled defaulting to false" fail
fi

# C5: message-job-service application.yml has award-credit-outbox.enabled (false default)
MJ_YML="$ROOT/big-market-message-job-service/src/main/resources/application.yml"
if grep -q "award-credit-outbox" "$MJ_YML" && \
   grep -A2 "award-credit-outbox" "$MJ_YML" | grep -q "false"; then
  check C5 "message-job-service application.yml has award-credit-outbox.enabled defaulting to false" ok
else
  check C5 "message-job-service application.yml has award-credit-outbox.enabled defaulting to false" fail
fi

echo ""
echo "── Required Docs ──────────────────────────────────────────────────────────"

# D1: Phase 2.3-C evidence doc exists
EVIDENCE_DOC="$ROOT/docs/evidence/phase-2-3-c-fulfillment-staging-readiness.md"
[ -f "$EVIDENCE_DOC" ] \
  && check D1 "Phase 2.3-C evidence doc exists (docs/evidence/phase-2-3-c-fulfillment-staging-readiness.md)" ok \
  || check D1 "Phase 2.3-C evidence doc exists (docs/evidence/phase-2-3-c-fulfillment-staging-readiness.md)" fail

# D2: Phase 2.3-C evidence doc contains job ownership decision
if [ -f "$EVIDENCE_DOC" ] && grep -q "DispatchCreditAwardTaskJob.*stays in message-job-service\|remains in message-job-service" "$EVIDENCE_DOC"; then
  check D2 "Phase 2.3-C evidence doc documents job ownership decision (stays in message-job-service)" ok
else
  check D2 "Phase 2.3-C evidence doc documents job ownership decision (stays in message-job-service)" fail
fi

# D3: Phase 2.3-C evidence doc contains GO/NO-GO criteria
if [ -f "$EVIDENCE_DOC" ] && grep -q "GO/NO-GO\|NO-GO" "$EVIDENCE_DOC"; then
  check D3 "Phase 2.3-C evidence doc contains GO/NO-GO criteria" ok
else
  check D3 "Phase 2.3-C evidence doc contains GO/NO-GO criteria" fail
fi

# D4: Phase 2.3-C evidence doc explicitly states production flags remain disabled
if [ -f "$EVIDENCE_DOC" ] && grep -q "Production flags remain.*false\|production flags remain disabled\|remain.*false.*throughout" "$EVIDENCE_DOC"; then
  check D4 "Phase 2.3-C evidence doc states production flags remain disabled" ok
else
  check D4 "Phase 2.3-C evidence doc states production flags remain disabled" fail
fi

# D5: Phase 2.3 design doc exists
DESIGN_DOC="$ROOT/docs/microservices-split-phase-2-3-fulfillment-service.md"
[ -f "$DESIGN_DOC" ] \
  && check D5 "Phase 2.3 design doc exists" ok \
  || check D5 "Phase 2.3 design doc exists" fail

# D6: Outbox SQL DDL file exists
OUTBOX_SQL="$ROOT/docs/sql/proposed-credit-award-task-outbox.sql"
[ -f "$OUTBOX_SQL" ] \
  && check D6 "Outbox DDL file exists (docs/sql/proposed-credit-award-task-outbox.sql)" ok \
  || check D6 "Outbox DDL file exists (docs/sql/proposed-credit-award-task-outbox.sql)" fail

echo ""
echo "── Required Scripts ───────────────────────────────────────────────────────"

# S1: B23-B validation script exists
[ -f "$ROOT/scripts/validate-fulfillment-service-b23-b.sh" ] \
  && check S1 "validate-fulfillment-service-b23-b.sh exists" ok \
  || check S1 "validate-fulfillment-service-b23-b.sh exists" fail

# S2: B9 E2E rehearsal script exists
[ -f "$ROOT/scripts/validate-award-credit-outbox-e2e-rehearsal.sh" ] \
  && check S2 "validate-award-credit-outbox-e2e-rehearsal.sh (B9) exists" ok \
  || check S2 "validate-award-credit-outbox-e2e-rehearsal.sh (B9) exists" fail

# S3: B6 outbox scaffold validation script exists
[ -f "$ROOT/scripts/validate-award-credit-outbox-b6.sh" ] \
  && check S3 "validate-award-credit-outbox-b6.sh (B6) exists" ok \
  || check S3 "validate-award-credit-outbox-b6.sh (B6) exists" fail

# S4: B10 production DDL script exists
[ -f "$ROOT/scripts/validate-production-ddl.sh" ] \
  && check S4 "validate-production-ddl.sh (B10) exists" ok \
  || check S4 "validate-production-ddl.sh (B10) exists" fail

echo ""
echo "── Adapter Wiring ─────────────────────────────────────────────────────────"

# A1: IAwardDispatchAdapter interface exists
[ -f "$ROOT/big-market-trigger/src/main/java/com/dyx/market/trigger/adapter/IAwardDispatchAdapter.java" ] \
  && check A1 "IAwardDispatchAdapter interface exists in big-market-trigger" ok \
  || check A1 "IAwardDispatchAdapter interface exists in big-market-trigger" fail

# A2: LocalAwardDispatchAdapter has @ConditionalOnMissingBean
LOCAL_ADAPTER="$ROOT/big-market-trigger/src/main/java/com/dyx/market/trigger/adapter/LocalAwardDispatchAdapter.java"
if [ -f "$LOCAL_ADAPTER" ] && grep -q "ConditionalOnMissingBean" "$LOCAL_ADAPTER"; then
  check A2 "LocalAwardDispatchAdapter has @ConditionalOnMissingBean" ok
else
  check A2 "LocalAwardDispatchAdapter has @ConditionalOnMissingBean" fail
fi

# A3: RemoteAwardDispatchAdapter has @DubboReference to IAwardService
REMOTE_ADAPTER="$ROOT/big-market-message-job-service/src/main/java/com/dyx/market/message/job/config/RemoteAwardDispatchAdapter.java"
if [ -f "$REMOTE_ADAPTER" ] && grep -q "DubboReference" "$REMOTE_ADAPTER" && grep -q "IAwardService" "$REMOTE_ADAPTER"; then
  check A3 "RemoteAwardDispatchAdapter has @DubboReference to IAwardService" ok
else
  check A3 "RemoteAwardDispatchAdapter has @DubboReference to IAwardService" fail
fi

# A4: SendAwardConsumer uses IAwardDispatchAdapter (not IAwardService directly)
CONSUMER="$ROOT/big-market-trigger/src/main/java/com/dyx/market/trigger/listener/SendAwardConsumer.java"
if [ -f "$CONSUMER" ] && grep -q "IAwardDispatchAdapter" "$CONSUMER" && ! grep -q "IAwardService" "$CONSUMER"; then
  check A4 "SendAwardConsumer injects IAwardDispatchAdapter (not IAwardService directly)" ok
else
  check A4 "SendAwardConsumer injects IAwardDispatchAdapter (not IAwardService directly)" fail
fi

# A5: RemoteAwardDispatchAdapter is guarded by @ConditionalOnProperty(havingValue=true)
WRITE_CONFIG="$ROOT/big-market-message-job-service/src/main/java/com/dyx/market/message/job/config/WriteAdapterLocalConfig.java"
if grep -q "account.fulfillment.remote-award.enabled" "$WRITE_CONFIG" 2>/dev/null && \
   grep -q "havingValue.*true" "$WRITE_CONFIG" 2>/dev/null; then
  check A5 "RemoteAwardDispatchAdapter registration guarded by @ConditionalOnProperty(havingValue=true)" ok
else
  check A5 "RemoteAwardDispatchAdapter registration guarded by @ConditionalOnProperty(havingValue=true)" fail
fi

echo ""
echo "── Provider Existence ─────────────────────────────────────────────────────"

# P1: FulfillmentAwardServiceRPC (Dubbo provider) exists
[ -f "$ROOT/big-market-fulfillment-service/src/main/java/com/dyx/market/fulfillment/provider/FulfillmentAwardServiceRPC.java" ] \
  && check P1 "FulfillmentAwardServiceRPC Dubbo provider exists in fulfillment-service" ok \
  || check P1 "FulfillmentAwardServiceRPC Dubbo provider exists in fulfillment-service" fail

# P2: fulfillment-service pom.xml exists (module intact)
[ -f "$ROOT/big-market-fulfillment-service/pom.xml" ] \
  && check P2 "big-market-fulfillment-service/pom.xml exists (module intact)" ok \
  || check P2 "big-market-fulfillment-service/pom.xml exists (module intact)" fail

# P3: FulfillmentServiceApplication scanBasePackages does NOT include trigger.job or message.job
# (XXL-Job handlers stay in message-job-service — only fulfillment, domain.award, infrastructure are scanned)
APP_FILE="$ROOT/big-market-fulfillment-service/src/main/java/com/dyx/market/fulfillment/FulfillmentServiceApplication.java"
# Extract only the scanBasePackages annotation value (between the @SpringBootApplication parens and the class declaration)
# then confirm it does not list trigger.job or message.job packages
SCAN_PACKAGES=$(awk '/scanBasePackages/,/\)$/' "$APP_FILE" 2>/dev/null | head -20)
if [ -f "$APP_FILE" ] && [ -n "$SCAN_PACKAGES" ] && \
   ! echo "$SCAN_PACKAGES" | grep -qE '"com\.dyx\.market\.trigger\.job|"com\.dyx\.market\.message\.job'; then
  check P3 "FulfillmentServiceApplication scanBasePackages does NOT include trigger.job or message.job" ok
else
  check P3 "FulfillmentServiceApplication scanBasePackages does NOT include trigger.job or message.job" fail
fi

# P4: fulfillment-service Dubbo port is 20882 (not conflicting with other services)
if grep -q "20882" "$FULFILLMENT_YML" 2>/dev/null; then
  check P4 "fulfillment-service Dubbo provider on port 20882" ok
else
  check P4 "fulfillment-service Dubbo provider on port 20882" fail
fi

echo ""
echo "── Job Ownership Documentation ────────────────────────────────────────────"

# J1: DispatchCreditAwardTaskJob is in message-job-service (not fulfillment-service)
JOB_IN_MJ="$ROOT/big-market-message-job-service/src/main/java/com/dyx/market/message/job/config/DispatchCreditAwardTaskJob.java"
[ -f "$JOB_IN_MJ" ] \
  && check J1 "DispatchCreditAwardTaskJob exists in message-job-service config" ok \
  || check J1 "DispatchCreditAwardTaskJob exists in message-job-service config" fail

# J2: DispatchCreditAwardTaskJob is NOT in fulfillment-service (no premature migration)
JOB_IN_FS=$(find "$ROOT/big-market-fulfillment-service/src" -name "DispatchCreditAwardTaskJob.java" 2>/dev/null | wc -l)
[ "$JOB_IN_FS" -eq 0 ] \
  && check J2 "DispatchCreditAwardTaskJob NOT in fulfillment-service (correct — stays in message-job-service)" ok \
  || check J2 "DispatchCreditAwardTaskJob NOT in fulfillment-service (correct — stays in message-job-service)" fail

# J3: credit_award_task_mapper.xml is in message-job-service (not yet in fulfillment-service)
MAPPER_IN_MJ="$ROOT/big-market-message-job-service/src/main/resources/mybatis/mapper/mysql/credit_award_task_mapper.xml"
[ -f "$MAPPER_IN_MJ" ] \
  && check J3 "credit_award_task_mapper.xml present in message-job-service (poller can read outbox rows)" ok \
  || check J3 "credit_award_task_mapper.xml present in message-job-service (poller can read outbox rows)" fail

# J4: DispatchCreditAwardTaskJob is guarded by @ConditionalOnProperty (won't activate until outbox DDL applied)
if grep -q "ConditionalOnProperty" "$JOB_IN_MJ" 2>/dev/null && \
   grep -q "award-credit-outbox.enabled" "$JOB_IN_MJ" 2>/dev/null; then
  check J4 "DispatchCreditAwardTaskJob is @ConditionalOnProperty-gated (safe when outbox flag=false)" ok
else
  check J4 "DispatchCreditAwardTaskJob is @ConditionalOnProperty-gated (safe when outbox flag=false)" fail
fi

# J5: DispatchCreditAwardTaskJob uses award_order_id as outBusinessNo (idempotency)
if grep -q "getAwardOrderId\(\)" "$JOB_IN_MJ" 2>/dev/null && \
   grep -q "outBusinessNo" "$JOB_IN_MJ" 2>/dev/null; then
  check J5 "DispatchCreditAwardTaskJob sets outBusinessNo=awardOrderId (idempotency key forwarding)" ok
else
  check J5 "DispatchCreditAwardTaskJob sets outBusinessNo=awardOrderId (idempotency key forwarding)" fail
fi

echo ""
echo "════════════════════════════════════════════════════════════════════════════"
echo "Results: $PASS PASS / $FAIL FAIL out of $((PASS + FAIL)) checks"
echo ""
if [ "$FAIL" -eq 0 ]; then
  echo "All B23-C readiness checks PASS. Repository is ready for staging validation."
  echo "Remaining blockers (require staging access):"
  echo "  - Phase 2.2-B17 staging GO (ledger DDL + outbox DDL + XXL-Job registration)"
  echo "  - Apply credit_award_task DDL to staging big_market_01 and big_market_02"
  echo "  - Register DispatchCreditAwardTaskJob_DB1/_DB2 in staging XXL-Job admin"
  echo "  - Execute E2E outbox validation (Phases 2-6 in evidence doc)"
  echo ""
  echo "See: docs/evidence/phase-2-3-c-fulfillment-staging-readiness.md"
  exit 0
else
  echo "B23-C readiness check FAILED. Fix the above before staging validation."
  exit 1
fi

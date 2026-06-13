#!/usr/bin/env bash
# validate-fulfillment-service-b23-d-production-gate.sh
# Phase 2.3-D: static pre-flight for the fulfillment-service production promotion gate.
# Requires no network, Docker, DB, or staging access. Safe to run locally at any time.

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

echo "=== Phase 2.3-D Production Gate Validation ==="
echo ""

# ── B23-D Evidence Document ───────────────────────────────────────────────────
echo "── B23-D Evidence Document ────────────────────────────────────────────────"

B23D_DOC="$ROOT/docs/evidence/phase-2-3-d-fulfillment-production-promotion-gate.md"

# D1: B23-D gate document exists
[ -f "$B23D_DOC" ] \
  && check D1 "B23-D production gate document exists" ok \
  || check D1 "B23-D production gate document exists" fail

# D2: Document references B23-C staging evidence dependency
if [ -f "$B23D_DOC" ] && grep -q "B23-C staging evidence\|phase-2-3-c-fulfillment-staging-readiness" "$B23D_DOC"; then
  check D2 "B23-D doc references B23-C staging evidence dependency" ok
else
  check D2 "B23-D doc references B23-C staging evidence dependency" fail
fi

# D3: Document contains deployment order section
if [ -f "$B23D_DOC" ] && grep -q "Deployment Order\|deployment order" "$B23D_DOC"; then
  check D3 "B23-D doc contains deployment order" ok
else
  check D3 "B23-D doc contains deployment order" fail
fi

# D4: Document contains rollback plan
if [ -f "$B23D_DOC" ] && grep -q "Rollback Plan\|rollback plan" "$B23D_DOC"; then
  check D4 "B23-D doc contains rollback plan" ok
else
  check D4 "B23-D doc contains rollback plan" fail
fi

# D5: Document contains GO/NO-GO criteria
if [ -f "$B23D_DOC" ] && grep -q "GO/NO-GO\|NO-GO" "$B23D_DOC"; then
  check D5 "B23-D doc contains GO/NO-GO criteria" ok
else
  check D5 "B23-D doc contains GO/NO-GO criteria" fail
fi

# D6: Document states DispatchCreditAwardTaskJob remains in message-job-service
if [ -f "$B23D_DOC" ] && grep -q "DispatchCreditAwardTaskJob.*message-job-service\|remains in.*message-job-service" "$B23D_DOC"; then
  check D6 "B23-D doc states DispatchCreditAwardTaskJob remains in message-job-service" ok
else
  check D6 "B23-D doc states DispatchCreditAwardTaskJob remains in message-job-service" fail
fi

# D7: Document states remote-award flag is false by default
if [ -f "$B23D_DOC" ] && grep -q "remote-award.*false\|REMOTE_AWARD.*false" "$B23D_DOC"; then
  check D7 "B23-D doc states remote-award flag is false by default" ok
else
  check D7 "B23-D doc states remote-award flag is false by default" fail
fi

# D8: Document explicitly states this batch does NOT enable production traffic
if [ -f "$B23D_DOC" ] && grep -q "does NOT enable production traffic\|BLOCKED until B23-C" "$B23D_DOC"; then
  check D8 "B23-D doc explicitly states this batch does NOT enable production traffic" ok
else
  check D8 "B23-D doc explicitly states this batch does NOT enable production traffic" fail
fi

echo ""
echo "── Config Safety (three dangerous flags must be false) ─────────────────────"

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

# C4: fulfillment-service application.yml has award-credit-outbox defaulting to false
FULFILLMENT_YML="$ROOT/big-market-fulfillment-service/src/main/resources/application.yml"
if grep -q "award-credit-outbox" "$FULFILLMENT_YML" 2>/dev/null && \
   grep -A3 "award-credit-outbox" "$FULFILLMENT_YML" | grep -q "ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED:false"; then
  check C4 "fulfillment-service application.yml: award-credit-outbox.enabled defaults to false" ok
else
  check C4 "fulfillment-service application.yml: award-credit-outbox.enabled defaults to false" fail
fi

# C5: message-job-service application.yml has award-credit-outbox defaulting to false
MJ_YML="$ROOT/big-market-message-job-service/src/main/resources/application.yml"
if grep -q "award-credit-outbox" "$MJ_YML" 2>/dev/null && \
   grep -A2 "award-credit-outbox" "$MJ_YML" | grep -q "false"; then
  check C5 "message-job-service application.yml: award-credit-outbox.enabled defaults to false" ok
else
  check C5 "message-job-service application.yml: award-credit-outbox.enabled defaults to false" fail
fi

echo ""
echo "── Adapter Wiring (B23-B/C re-check) ──────────────────────────────────────"

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

# A5: RemoteAwardDispatchAdapter guarded by @ConditionalOnProperty(havingValue=true)
WRITE_CONFIG="$ROOT/big-market-message-job-service/src/main/java/com/dyx/market/message/job/config/WriteAdapterLocalConfig.java"
if grep -q "account.fulfillment.remote-award.enabled" "$WRITE_CONFIG" 2>/dev/null && \
   grep -q "havingValue.*true" "$WRITE_CONFIG" 2>/dev/null; then
  check A5 "RemoteAwardDispatchAdapter guarded by @ConditionalOnProperty(havingValue=true)" ok
else
  check A5 "RemoteAwardDispatchAdapter guarded by @ConditionalOnProperty(havingValue=true)" fail
fi

echo ""
echo "── Job Ownership (DispatchCreditAwardTaskJob in message-job-service) ────────"

# J1: DispatchCreditAwardTaskJob exists in message-job-service
JOB_IN_MJ="$ROOT/big-market-message-job-service/src/main/java/com/dyx/market/message/job/config/DispatchCreditAwardTaskJob.java"
[ -f "$JOB_IN_MJ" ] \
  && check J1 "DispatchCreditAwardTaskJob exists in message-job-service" ok \
  || check J1 "DispatchCreditAwardTaskJob exists in message-job-service" fail

# J2: DispatchCreditAwardTaskJob NOT in fulfillment-service
JOB_IN_FS=$(find "$ROOT/big-market-fulfillment-service/src" -name "DispatchCreditAwardTaskJob.java" 2>/dev/null | wc -l)
[ "$JOB_IN_FS" -eq 0 ] \
  && check J2 "DispatchCreditAwardTaskJob NOT in fulfillment-service (correct placement)" ok \
  || check J2 "DispatchCreditAwardTaskJob NOT in fulfillment-service (found $JOB_IN_FS — premature migration!)" fail

# J3: DispatchCreditAwardTaskJob is @ConditionalOnProperty-gated
if grep -q "ConditionalOnProperty" "$JOB_IN_MJ" 2>/dev/null && \
   grep -q "award-credit-outbox.enabled" "$JOB_IN_MJ" 2>/dev/null; then
  check J3 "DispatchCreditAwardTaskJob is @ConditionalOnProperty-gated on outbox flag" ok
else
  check J3 "DispatchCreditAwardTaskJob is @ConditionalOnProperty-gated on outbox flag" fail
fi

# J4: credit_award_task_mapper.xml is in message-job-service
MAPPER="$ROOT/big-market-message-job-service/src/main/resources/mybatis/mapper/mysql/credit_award_task_mapper.xml"
[ -f "$MAPPER" ] \
  && check J4 "credit_award_task_mapper.xml present in message-job-service" ok \
  || check J4 "credit_award_task_mapper.xml present in message-job-service" fail

echo ""
echo "── Provider and Module Integrity ───────────────────────────────────────────"

# P1: FulfillmentAwardServiceRPC exists in fulfillment-service
[ -f "$ROOT/big-market-fulfillment-service/src/main/java/com/dyx/market/fulfillment/provider/FulfillmentAwardServiceRPC.java" ] \
  && check P1 "FulfillmentAwardServiceRPC Dubbo provider exists in fulfillment-service" ok \
  || check P1 "FulfillmentAwardServiceRPC Dubbo provider exists in fulfillment-service" fail

# P2: fulfillment-service pom.xml exists (module intact)
[ -f "$ROOT/big-market-fulfillment-service/pom.xml" ] \
  && check P2 "big-market-fulfillment-service/pom.xml exists (module intact)" ok \
  || check P2 "big-market-fulfillment-service/pom.xml exists (module intact)" fail

# P3: FulfillmentServiceApplication scanBasePackages does NOT include trigger.job / message.job
APP_FILE="$ROOT/big-market-fulfillment-service/src/main/java/com/dyx/market/fulfillment/FulfillmentServiceApplication.java"
SCAN_PACKAGES=$(awk '/scanBasePackages/,/\)$/' "$APP_FILE" 2>/dev/null | head -20)
if [ -f "$APP_FILE" ] && [ -n "$SCAN_PACKAGES" ] && \
   ! echo "$SCAN_PACKAGES" | grep -qE '"com\.dyx\.market\.trigger\.job|"com\.dyx\.market\.message\.job'; then
  check P3 "FulfillmentServiceApplication scanBasePackages excludes trigger.job / message.job" ok
else
  check P3 "FulfillmentServiceApplication scanBasePackages excludes trigger.job / message.job" fail
fi

# P4: fulfillment-service Dubbo provider is on port 20882
if grep -q "20882" "$FULFILLMENT_YML" 2>/dev/null; then
  check P4 "fulfillment-service Dubbo provider on port 20882" ok
else
  check P4 "fulfillment-service Dubbo provider on port 20882" fail
fi

echo ""
echo "── Required Docs and Scripts ───────────────────────────────────────────────"

# DS1: B23-D validator script itself exists (self-check)
[ -f "$ROOT/scripts/validate-fulfillment-service-b23-d-production-gate.sh" ] \
  && check DS1 "validate-fulfillment-service-b23-d-production-gate.sh exists" ok \
  || check DS1 "validate-fulfillment-service-b23-d-production-gate.sh exists" fail

# DS2: B23-C evidence doc exists
[ -f "$ROOT/docs/evidence/phase-2-3-c-fulfillment-staging-readiness.md" ] \
  && check DS2 "Phase 2.3-C staging evidence doc exists" ok \
  || check DS2 "Phase 2.3-C staging evidence doc exists" fail

# DS3: B23-B validator script exists
[ -f "$ROOT/scripts/validate-fulfillment-service-b23-b.sh" ] \
  && check DS3 "validate-fulfillment-service-b23-b.sh (B23-B) exists" ok \
  || check DS3 "validate-fulfillment-service-b23-b.sh (B23-B) exists" fail

# DS4: B23-C validator script exists
[ -f "$ROOT/scripts/validate-fulfillment-service-b23-c-readiness.sh" ] \
  && check DS4 "validate-fulfillment-service-b23-c-readiness.sh (B23-C) exists" ok \
  || check DS4 "validate-fulfillment-service-b23-c-readiness.sh (B23-C) exists" fail

# DS5: Outbox DDL file exists
[ -f "$ROOT/docs/sql/proposed-credit-award-task-outbox.sql" ] \
  && check DS5 "Outbox DDL file exists (docs/sql/proposed-credit-award-task-outbox.sql)" ok \
  || check DS5 "Outbox DDL file exists (docs/sql/proposed-credit-award-task-outbox.sql)" fail

# DS6: Phase 2.3 design doc exists and has Phase 2.3-D section
DESIGN_DOC="$ROOT/docs/archive/phases.md"
if [ -f "$DESIGN_DOC" ] && grep -q "Phase 2.3-D\|2\.3-D" "$DESIGN_DOC"; then
  check DS6 "Phase 2.3 design doc has Phase 2.3-D section" ok
else
  check DS6 "Phase 2.3 design doc has Phase 2.3-D section" fail
fi

echo ""
echo "════════════════════════════════════════════════════════════════════════════"
echo "Results: $PASS PASS / $FAIL FAIL out of $((PASS + FAIL)) checks"
echo ""
if [ "$FAIL" -eq 0 ]; then
  echo "All B23-D production gate pre-flight checks PASS."
  echo ""
  echo "Remaining blockers (require staging access and sign-off):"
  echo "  - B23-C staging evidence (SE1–SE11) must be completed and attached"
  echo "  - B23-C staging GO decision must be signed off by oncall lead"
  echo "  - DBA must apply credit_award_task DDL to production big_market_01 and big_market_02"
  echo "  - Ops must register DispatchCreditAwardTaskJob_DB1/_DB2 in production XXL-Job admin"
  echo "  - Oncall lead must approve production flag enable window before step 5"
  echo ""
  echo "See: docs/evidence/phase-2-3-d-fulfillment-production-promotion-gate.md"
  exit 0
else
  echo "B23-D production gate pre-flight FAILED. Fix the above before any production action."
  exit 1
fi

#!/usr/bin/env bash
# validate-fulfillment-service-b23-e-cutover-execution.sh
# Phase 2.3-E static validator: cutover execution pack readiness.
# No network, Docker, DB, staging, or production access required. Safe to run locally.

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

echo "=== Phase 2.3-E Cutover Execution Pack Validation ==="
echo ""

B23E_DOC="$ROOT/docs/evidence/phase-2-3-e-fulfillment-cutover-execution.md"

# ── B23-E Evidence Document ───────────────────────────────────────────────────
echo "── B23-E Evidence Document ────────────────────────────────────────────────"

# E1: B23-E evidence doc exists
[ -f "$B23E_DOC" ] \
  && check E1 "B23-E cutover execution doc exists (docs/evidence/phase-2-3-e-fulfillment-cutover-execution.md)" ok \
  || check E1 "B23-E cutover execution doc exists (docs/evidence/phase-2-3-e-fulfillment-cutover-execution.md)" fail

# E2: Doc references B23-C dependency
if [ -f "$B23E_DOC" ] && grep -q "B23-C\|phase-2-3-c-fulfillment-staging-readiness" "$B23E_DOC"; then
  check E2 "B23-E doc references B23-C staging evidence dependency" ok
else
  check E2 "B23-E doc references B23-C staging evidence dependency" fail
fi

# E3: Doc references B23-D dependency
if [ -f "$B23E_DOC" ] && grep -q "B23-D\|phase-2-3-d-fulfillment-production-promotion-gate" "$B23E_DOC"; then
  check E3 "B23-E doc references B23-D production gate dependency" ok
else
  check E3 "B23-E doc references B23-D production gate dependency" fail
fi

# E4: Doc contains staging cutover steps section
if [ -f "$B23E_DOC" ] && grep -q "Staging Cutover Steps\|staging cutover" "$B23E_DOC"; then
  check E4 "B23-E doc contains staging cutover steps" ok
else
  check E4 "B23-E doc contains staging cutover steps" fail
fi

# E5: Doc contains production cutover steps section
if [ -f "$B23E_DOC" ] && grep -q "Production Cutover Steps\|production cutover" "$B23E_DOC"; then
  check E5 "B23-E doc contains production cutover steps" ok
else
  check E5 "B23-E doc contains production cutover steps" fail
fi

# E6: Doc contains rollback commands
if [ -f "$B23E_DOC" ] && grep -q "Rollback Commands\|rollback" "$B23E_DOC"; then
  check E6 "B23-E doc contains rollback commands" ok
else
  check E6 "B23-E doc contains rollback commands" fail
fi

# E7: Doc contains GO/NO-GO criteria
if [ -f "$B23E_DOC" ] && grep -q "GO/NO-GO\|NO-GO" "$B23E_DOC"; then
  check E7 "B23-E doc contains GO/NO-GO criteria" ok
else
  check E7 "B23-E doc contains GO/NO-GO criteria" fail
fi

# E8: Doc explicitly states this repo batch does NOT enable traffic
if [ -f "$B23E_DOC" ] && grep -q "does NOT enable.*traffic\|does not enable.*traffic" "$B23E_DOC"; then
  check E8 "B23-E doc states this repo batch does NOT enable traffic" ok
else
  check E8 "B23-E doc states this repo batch does NOT enable traffic" fail
fi

# E9: Doc explicitly states DispatchCreditAwardTaskJob remains in message-job-service
if [ -f "$B23E_DOC" ] && grep -q "DispatchCreditAwardTaskJob.*message-job-service\|remains in.*message-job-service" "$B23E_DOC"; then
  check E9 "B23-E doc states DispatchCreditAwardTaskJob remains in message-job-service" ok
else
  check E9 "B23-E doc states DispatchCreditAwardTaskJob remains in message-job-service" fail
fi

# E10: Doc confirms remote-award flag defaults to false
if [ -f "$B23E_DOC" ] && grep -q "remote-award.*false\|REMOTE_AWARD.*false" "$B23E_DOC"; then
  check E10 "B23-E doc confirms remote-award flag defaults to false" ok
else
  check E10 "B23-E doc confirms remote-award flag defaults to false" fail
fi

echo ""
echo "── Config Safety (three dangerous flags must be false) ─────────────────────"

# C1: account.award-credit-outbox.enabled NOT true in any config
TRUE_OUTBOX=0
while IFS= read -r f; do
  if grep -qE "ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED:true" "$f" 2>/dev/null; then
    TRUE_OUTBOX=$((TRUE_OUTBOX + 1))
  fi
done < <(find "$ROOT" -not -path "*/target/*" \( -name "*.yml" -o -name "*.properties" \) 2>/dev/null)
[ "$TRUE_OUTBOX" -eq 0 ] \
  && check C1 "account.award-credit-outbox.enabled NOT true by default in any config" ok \
  || check C1 "account.award-credit-outbox.enabled NOT true by default in any config (found $TRUE_OUTBOX)" fail

# C2: account.fulfillment.remote-award.enabled NOT true in any config
TRUE_AWARD=0
while IFS= read -r f; do
  if grep -qE "ACCOUNT_FULFILLMENT_REMOTE_AWARD_ENABLED:true" "$f" 2>/dev/null; then
    TRUE_AWARD=$((TRUE_AWARD + 1))
  fi
done < <(find "$ROOT" -not -path "*/target/*" \( -name "*.yml" -o -name "*.properties" \) 2>/dev/null)
[ "$TRUE_AWARD" -eq 0 ] \
  && check C2 "account.fulfillment.remote-award.enabled NOT true by default in any config" ok \
  || check C2 "account.fulfillment.remote-award.enabled NOT true by default in any config (found $TRUE_AWARD)" fail

# C3: account.service.remote-quota-decrement.enabled NOT true in any config
TRUE_QUOTA=0
while IFS= read -r f; do
  if grep -qE "remote-quota-decrement.*enabled.*: true|REMOTE_QUOTA_DECREMENT.*:true" "$f" 2>/dev/null; then
    TRUE_QUOTA=$((TRUE_QUOTA + 1))
  fi
done < <(find "$ROOT" -not -path "*/target/*" \( -name "*.yml" -o -name "*.properties" \) 2>/dev/null)
[ "$TRUE_QUOTA" -eq 0 ] \
  && check C3 "account.service.remote-quota-decrement.enabled NOT true by default in any config" ok \
  || check C3 "account.service.remote-quota-decrement.enabled NOT true by default in any config (found $TRUE_QUOTA)" fail

# C4: fulfillment-service application.yml defaults outbox flag to false
FULFILLMENT_YML="$ROOT/big-market-fulfillment-service/src/main/resources/application.yml"
if grep -q "award-credit-outbox" "$FULFILLMENT_YML" 2>/dev/null && \
   grep -A3 "award-credit-outbox" "$FULFILLMENT_YML" | grep -q "ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED:false"; then
  check C4 "fulfillment-service application.yml: award-credit-outbox.enabled defaults to false" ok
else
  check C4 "fulfillment-service application.yml: award-credit-outbox.enabled defaults to false" fail
fi

# C5: message-job-service application.yml defaults outbox flag to false
MJ_YML="$ROOT/big-market-message-job-service/src/main/resources/application.yml"
if grep -q "award-credit-outbox" "$MJ_YML" 2>/dev/null && \
   grep -A2 "award-credit-outbox" "$MJ_YML" | grep -q "false"; then
  check C5 "message-job-service application.yml: award-credit-outbox.enabled defaults to false" ok
else
  check C5 "message-job-service application.yml: award-credit-outbox.enabled defaults to false" fail
fi

echo ""
echo "── Adapter Wiring (B23-B/C/D re-check) ────────────────────────────────────"

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

# A6: RemoteAwardDispatchAdapter re-throws RpcException (not swallowed)
if [ -f "$REMOTE_ADAPTER" ] && grep -q "RpcException" "$REMOTE_ADAPTER" && grep -q "throw" "$REMOTE_ADAPTER"; then
  check A6 "RemoteAwardDispatchAdapter re-throws RpcException (not swallowed)" ok
else
  check A6 "RemoteAwardDispatchAdapter re-throws RpcException (not swallowed)" fail
fi

echo ""
echo "── Job Ownership (DispatchCreditAwardTaskJob in message-job-service) ────────"

JOB_IN_MJ="$ROOT/big-market-message-job-service/src/main/java/com/dyx/market/message/job/config/DispatchCreditAwardTaskJob.java"

# J1: DispatchCreditAwardTaskJob exists in message-job-service
[ -f "$JOB_IN_MJ" ] \
  && check J1 "DispatchCreditAwardTaskJob exists in message-job-service" ok \
  || check J1 "DispatchCreditAwardTaskJob exists in message-job-service" fail

# J2: DispatchCreditAwardTaskJob NOT in fulfillment-service
JOB_IN_FS=$(find "$ROOT/big-market-fulfillment-service/src" -name "DispatchCreditAwardTaskJob.java" 2>/dev/null | wc -l)
[ "$JOB_IN_FS" -eq 0 ] \
  && check J2 "DispatchCreditAwardTaskJob NOT in fulfillment-service (correct — stays in message-job-service)" ok \
  || check J2 "DispatchCreditAwardTaskJob NOT in fulfillment-service (found $JOB_IN_FS — premature migration!)" fail

# J3: DispatchCreditAwardTaskJob is @ConditionalOnProperty-gated on outbox flag
if grep -q "ConditionalOnProperty" "$JOB_IN_MJ" 2>/dev/null && \
   grep -q "award-credit-outbox.enabled" "$JOB_IN_MJ" 2>/dev/null; then
  check J3 "DispatchCreditAwardTaskJob is @ConditionalOnProperty-gated on outbox flag" ok
else
  check J3 "DispatchCreditAwardTaskJob is @ConditionalOnProperty-gated on outbox flag" fail
fi

# J4: credit_award_task_mapper.xml present in message-job-service
MAPPER="$ROOT/big-market-message-job-service/src/main/resources/mybatis/mapper/mysql/credit_award_task_mapper.xml"
[ -f "$MAPPER" ] \
  && check J4 "credit_award_task_mapper.xml present in message-job-service (poller reads outbox rows)" ok \
  || check J4 "credit_award_task_mapper.xml present in message-job-service (poller reads outbox rows)" fail

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

# P3: FulfillmentServiceApplication scanBasePackages excludes trigger.job / message.job
APP_FILE="$ROOT/big-market-fulfillment-service/src/main/java/com/dyx/market/fulfillment/FulfillmentServiceApplication.java"
SCAN_PACKAGES=$(awk '/scanBasePackages/,/\)$/' "$APP_FILE" 2>/dev/null | head -20)
if [ -f "$APP_FILE" ] && [ -n "$SCAN_PACKAGES" ] && \
   ! echo "$SCAN_PACKAGES" | grep -qE '"com\.dyx\.market\.trigger\.job|"com\.dyx\.market\.message\.job'; then
  check P3 "FulfillmentServiceApplication scanBasePackages excludes trigger.job / message.job" ok
else
  check P3 "FulfillmentServiceApplication scanBasePackages excludes trigger.job / message.job" fail
fi

# P4: fulfillment-service Dubbo provider on port 20882
if grep -q "20882" "$FULFILLMENT_YML" 2>/dev/null; then
  check P4 "fulfillment-service Dubbo provider on port 20882" ok
else
  check P4 "fulfillment-service Dubbo provider on port 20882" fail
fi

echo ""
echo "── Required Docs and Scripts ───────────────────────────────────────────────"

# DS1: B23-E validator script itself exists (self-check)
[ -f "$ROOT/scripts/validate-fulfillment-service-b23-e-cutover-execution.sh" ] \
  && check DS1 "validate-fulfillment-service-b23-e-cutover-execution.sh exists (self-check)" ok \
  || check DS1 "validate-fulfillment-service-b23-e-cutover-execution.sh exists (self-check)" fail

# DS2: B23-D evidence doc exists
[ -f "$ROOT/docs/evidence/phase-2-3-d-fulfillment-production-promotion-gate.md" ] \
  && check DS2 "Phase 2.3-D production gate doc exists" ok \
  || check DS2 "Phase 2.3-D production gate doc exists" fail

# DS3: B23-C evidence doc exists
[ -f "$ROOT/docs/evidence/phase-2-3-c-fulfillment-staging-readiness.md" ] \
  && check DS3 "Phase 2.3-C staging evidence doc exists" ok \
  || check DS3 "Phase 2.3-C staging evidence doc exists" fail

# DS4: B23-D validator exists
[ -f "$ROOT/scripts/validate-fulfillment-service-b23-d-production-gate.sh" ] \
  && check DS4 "validate-fulfillment-service-b23-d-production-gate.sh exists" ok \
  || check DS4 "validate-fulfillment-service-b23-d-production-gate.sh exists" fail

# DS5: B23-C validator exists
[ -f "$ROOT/scripts/validate-fulfillment-service-b23-c-readiness.sh" ] \
  && check DS5 "validate-fulfillment-service-b23-c-readiness.sh exists" ok \
  || check DS5 "validate-fulfillment-service-b23-c-readiness.sh exists" fail

# DS6: B23-B validator exists
[ -f "$ROOT/scripts/validate-fulfillment-service-b23-b.sh" ] \
  && check DS6 "validate-fulfillment-service-b23-b.sh exists" ok \
  || check DS6 "validate-fulfillment-service-b23-b.sh exists" fail

# DS7: Outbox DDL file exists
[ -f "$ROOT/docs/sql/proposed-credit-award-task-outbox.sql" ] \
  && check DS7 "Outbox DDL file exists (docs/sql/proposed-credit-award-task-outbox.sql)" ok \
  || check DS7 "Outbox DDL file exists (docs/sql/proposed-credit-award-task-outbox.sql)" fail

# DS8: Phase 2.3 design doc has Phase 2.3-E section
DESIGN_DOC="$ROOT/docs/microservices-split-phase-2-3-fulfillment-service.md"
if [ -f "$DESIGN_DOC" ] && grep -q "Phase 2.3-E\|2\.3-E" "$DESIGN_DOC"; then
  check DS8 "Phase 2.3 design doc has Phase 2.3-E section" ok
else
  check DS8 "Phase 2.3 design doc has Phase 2.3-E section" fail
fi

echo ""
echo "════════════════════════════════════════════════════════════════════════════"
echo "Results: $PASS PASS / $FAIL FAIL out of $((PASS + FAIL)) checks"
echo ""
if [ "$FAIL" -eq 0 ]; then
  echo "All B23-E cutover execution pack checks PASS. Repo is ready for the actual cutover."
  echo ""
  echo "Remaining blockers (require staging access, real sign-offs, and a cutover window):"
  echo "  - B23-C staging evidence (SE1–SE11) must be completed and signed off by oncall lead"
  echo "  - B23-D evidence file must be completed and signed"
  echo "  - DBA must apply credit_award_task DDL to production big_market_01 and big_market_02"
  echo "  - Ops must register DispatchCreditAwardTaskJob_DB1/_DB2 in production XXL-Job admin"
  echo "  - Oncall lead must issue written approval for the production cutover window"
  echo "  - Execute staging cutover steps (S1–S8) and production cutover steps (P1–P8)"
  echo "    as documented in docs/evidence/phase-2-3-e-fulfillment-cutover-execution.md"
  echo ""
  echo "See: docs/evidence/phase-2-3-e-fulfillment-cutover-execution.md"
  exit 0
else
  echo "B23-E cutover execution pack check FAILED. Fix the above before proceeding."
  exit 1
fi

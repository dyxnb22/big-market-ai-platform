#!/usr/bin/env bash
# validate-fulfillment-service-b23-b.sh
# Phase 2.3-B static validation: Dubbo client wiring scaffold for award dispatch adapter.
# Static checks only — no Docker, no DB, no network required.

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

# S1: IAwardDispatchAdapter interface exists in big-market-trigger
INTERFACE_FILE="$ROOT/big-market-trigger/src/main/java/com/dyx/market/trigger/adapter/IAwardDispatchAdapter.java"
[ -f "$INTERFACE_FILE" ] && check S1 "IAwardDispatchAdapter interface exists in big-market-trigger" ok \
  || check S1 "IAwardDispatchAdapter interface exists in big-market-trigger" fail

# S2: LocalAwardDispatchAdapter exists and has @ConditionalOnMissingBean
LOCAL_FILE="$ROOT/big-market-trigger/src/main/java/com/dyx/market/trigger/adapter/LocalAwardDispatchAdapter.java"
if [ -f "$LOCAL_FILE" ] && grep -q "ConditionalOnMissingBean" "$LOCAL_FILE"; then
  check S2 "LocalAwardDispatchAdapter exists with @ConditionalOnMissingBean" ok
else
  check S2 "LocalAwardDispatchAdapter exists with @ConditionalOnMissingBean" fail
fi

# S3: LocalAwardDispatchAdapter delegates to IAwardService (not a stub/no-op)
if [ -f "$LOCAL_FILE" ] && grep -q "IAwardService" "$LOCAL_FILE" && grep -q "distributeAward" "$LOCAL_FILE"; then
  check S3 "LocalAwardDispatchAdapter delegates to IAwardService" ok
else
  check S3 "LocalAwardDispatchAdapter delegates to IAwardService" fail
fi

# S4: RemoteAwardDispatchAdapter exists in message-job-service config
REMOTE_FILE="$ROOT/big-market-message-job-service/src/main/java/com/dyx/market/message/job/config/RemoteAwardDispatchAdapter.java"
[ -f "$REMOTE_FILE" ] && check S4 "RemoteAwardDispatchAdapter exists in message-job-service config" ok \
  || check S4 "RemoteAwardDispatchAdapter exists in message-job-service config" fail

# S5: RemoteAwardDispatchAdapter has @ConditionalOnProperty name=account.fulfillment.remote-award.enabled havingValue=true
# Check WriteAdapterLocalConfig for the @Bean registration with @ConditionalOnProperty
WRITE_ADAPTER_CONFIG="$ROOT/big-market-message-job-service/src/main/java/com/dyx/market/message/job/config/WriteAdapterLocalConfig.java"
if grep -q "account.fulfillment.remote-award.enabled" "$WRITE_ADAPTER_CONFIG" && grep -q "havingValue.*true" "$WRITE_ADAPTER_CONFIG"; then
  check S5 "RemoteAwardDispatchAdapter registered with @ConditionalOnProperty(havingValue=true)" ok
else
  check S5 "RemoteAwardDispatchAdapter registered with @ConditionalOnProperty(havingValue=true)" fail
fi

# S6: RemoteAwardDispatchAdapter has @DubboReference to IAwardService
if [ -f "$REMOTE_FILE" ] && grep -q "DubboReference" "$REMOTE_FILE" && grep -q "IAwardService" "$REMOTE_FILE"; then
  check S6 "RemoteAwardDispatchAdapter has @DubboReference to IAwardService" ok
else
  check S6 "RemoteAwardDispatchAdapter has @DubboReference to IAwardService" fail
fi

# S7: RemoteAwardDispatchAdapter does NOT swallow RpcException silently (must throw/rethrow)
if [ -f "$REMOTE_FILE" ] && grep -q "RpcException" "$REMOTE_FILE" && grep -q "throw" "$REMOTE_FILE"; then
  check S7 "RemoteAwardDispatchAdapter re-throws RpcException (not swallowed)" ok
else
  check S7 "RemoteAwardDispatchAdapter re-throws RpcException (not swallowed)" fail
fi

# S8: SendAwardConsumer injects IAwardDispatchAdapter (not IAwardService directly)
CONSUMER_FILE="$ROOT/big-market-trigger/src/main/java/com/dyx/market/trigger/listener/SendAwardConsumer.java"
if [ -f "$CONSUMER_FILE" ] && grep -q "IAwardDispatchAdapter" "$CONSUMER_FILE" && ! grep -q "IAwardService" "$CONSUMER_FILE"; then
  check S8 "SendAwardConsumer injects IAwardDispatchAdapter (not IAwardService directly)" ok
else
  check S8 "SendAwardConsumer injects IAwardDispatchAdapter (not IAwardService directly)" fail
fi

# S9: account.fulfillment.remote-award.enabled=false in message-job-service config
MJ_YML="$ROOT/big-market-message-job-service/src/main/resources/application.yml"
if grep -q "remote-award" "$MJ_YML" && grep -A2 "remote-award" "$MJ_YML" | grep -q "enabled.*false\|false"; then
  check S9 "account.fulfillment.remote-award.enabled=false in message-job-service config" ok
else
  check S9 "account.fulfillment.remote-award.enabled=false in message-job-service config" fail
fi

# S10: account.fulfillment.remote-award.enabled=false in big-market-app config
APP_YML="$ROOT/big-market-app/src/main/resources/application-dev.yml"
if grep -q "remote-award" "$APP_YML" && grep -A2 "remote-award" "$APP_YML" | grep -q "enabled.*false\|false"; then
  check S10 "account.fulfillment.remote-award.enabled=false in big-market-app config" ok
else
  check S10 "account.fulfillment.remote-award.enabled=false in big-market-app config" fail
fi

# S11: docker-compose.yml passes ACCOUNT_FULFILLMENT_REMOTE_AWARD_ENABLED
if grep -q "ACCOUNT_FULFILLMENT_REMOTE_AWARD_ENABLED" "$ROOT/docker-compose.yml"; then
  check S11 "docker-compose.yml passes ACCOUNT_FULFILLMENT_REMOTE_AWARD_ENABLED" ok
else
  check S11 "docker-compose.yml passes ACCOUNT_FULFILLMENT_REMOTE_AWARD_ENABLED" fail
fi

# S12: docker-compose.yml default for ACCOUNT_FULFILLMENT_REMOTE_AWARD_ENABLED is false
if grep "ACCOUNT_FULFILLMENT_REMOTE_AWARD_ENABLED" "$ROOT/docker-compose.yml" | grep -q ":-false"; then
  check S12 "docker-compose.yml default for ACCOUNT_FULFILLMENT_REMOTE_AWARD_ENABLED is false" ok
else
  check S12 "docker-compose.yml default for ACCOUNT_FULFILLMENT_REMOTE_AWARD_ENABLED is false" fail
fi

# S13: account.award-credit-outbox.enabled=true ZERO occurrences in all configs
# Check for literal 'true' or env-var default ':true' patterns, excluding target/
TRUE_OUTBOX=0
while IFS= read -r f; do
  if grep -qE "ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED:true" "$f" 2>/dev/null; then
    TRUE_OUTBOX=$((TRUE_OUTBOX + 1))
  fi
done < <(find "$ROOT" -not -path "*/target/*" \( -name "*.yml" -o -name "*.properties" \) 2>/dev/null)
if [ "$TRUE_OUTBOX" -eq 0 ]; then
  check S13 "account.award-credit-outbox.enabled=true ZERO occurrences in all configs" ok
else
  check S13 "account.award-credit-outbox.enabled=true ZERO occurrences in all configs (found $TRUE_OUTBOX)" fail
fi

# S14: remote-quota-decrement.enabled=true ZERO occurrences in all configs
TRUE_QUOTA=0
while IFS= read -r f; do
  if grep -qE "remote-quota-decrement.*enabled.*: true|REMOTE_QUOTA_DECREMENT.*:true" "$f" 2>/dev/null; then
    TRUE_QUOTA=$((TRUE_QUOTA + 1))
  fi
done < <(find "$ROOT" -not -path "*/target/*" \( -name "*.yml" -o -name "*.properties" \) 2>/dev/null)
if [ "$TRUE_QUOTA" -eq 0 ]; then
  check S14 "remote-quota-decrement.enabled=true ZERO occurrences in all configs" ok
else
  check S14 "remote-quota-decrement.enabled=true ZERO occurrences in all configs (found $TRUE_QUOTA)" fail
fi

# S15: FulfillmentAwardServiceRPC (provider) still exists in fulfillment-service
RPC_FILE="$ROOT/big-market-fulfillment-service/src/main/java/com/dyx/market/fulfillment/provider/FulfillmentAwardServiceRPC.java"
[ -f "$RPC_FILE" ] && check S15 "FulfillmentAwardServiceRPC provider still exists in fulfillment-service" ok \
  || check S15 "FulfillmentAwardServiceRPC provider still exists in fulfillment-service" fail

# S16: big-market-fulfillment-service/pom.xml exists (fulfillment-service module intact)
FULFILLMENT_POM="$ROOT/big-market-fulfillment-service/pom.xml"
[ -f "$FULFILLMENT_POM" ] && check S16 "big-market-fulfillment-service/pom.xml exists (module intact)" ok \
  || check S16 "big-market-fulfillment-service/pom.xml exists (module intact)" fail

echo ""
echo "Results: $PASS PASS / $FAIL FAIL out of $((PASS + FAIL)) checks"
[ "$FAIL" -eq 0 ] && exit 0 || exit 1

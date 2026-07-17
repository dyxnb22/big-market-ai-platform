#!/usr/bin/env bash
set -euo pipefail

# Start optional dedicated providers and recreate the consumers that own the
# embedded/remote switch. This prevents a half-switched local stack where a
# provider is running but market/message-job still use their old defaults.

usage() {
  cat <<'USAGE'
Usage: scripts/start-provider-mode.sh [--secure] <fulfillment|rebate-strategy|all>

Modes:
  fulfillment       Start fulfillment-service and recreate message-job with remote award enabled.
  rebate-strategy   Start rebate/strategy and recreate market with remote providers enabled.
  all               Start all three providers and recreate market/message-job.

Options:
  --secure          Secure the full default application stack plus the selected providers;
                    required secure variables must be exported first.

Examples:
  scripts/start-provider-mode.sh fulfillment
  scripts/start-provider-mode.sh rebate-strategy
  scripts/start-provider-mode.sh --secure all
USAGE
}

mode=""
secure=false
for arg in "$@"; do
  case "$arg" in
    --secure)
      secure=true
      ;;
    fulfillment|rebate-strategy|all)
      if [ -n "$mode" ]; then
        echo "ERROR: provider mode specified more than once: $mode and $arg" >&2
        usage >&2
        exit 2
      fi
      mode="$arg"
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "ERROR: unknown argument: $arg" >&2
      usage >&2
      exit 2
      ;;
  esac
done

if [ -z "$mode" ]; then
  usage >&2
  exit 2
fi

compose_files=(-f docker-compose.yml -f docker-compose.providers.yml)
if [ "$secure" = true ]; then
  compose_files+=(-f docker-compose.secure.yml)
fi

services=()
case "$mode" in
  fulfillment)
    export ACCOUNT_FULFILLMENT_REMOTE_AWARD_ENABLED=true
    services=(big-market-message-job-service big-market-fulfillment-service)
    ;;
  rebate-strategy)
    export REBATE_EMBEDDED_RPC_PROVIDER_ENABLED=false
    export REBATE_SERVICE_REMOTE_CREATE_ORDER_ENABLED=true
    export STRATEGY_EMBEDDED_RPC_PROVIDER_ENABLED=false
    export STRATEGY_SERVICE_REMOTE_READ_ENABLED=true
    services=(big-market-market-service big-market-rebate-service big-market-strategy-service)
    ;;
  all)
    export ACCOUNT_FULFILLMENT_REMOTE_AWARD_ENABLED=true
    export REBATE_EMBEDDED_RPC_PROVIDER_ENABLED=false
    export REBATE_SERVICE_REMOTE_CREATE_ORDER_ENABLED=true
    export STRATEGY_EMBEDDED_RPC_PROVIDER_ENABLED=false
    export STRATEGY_SERVICE_REMOTE_READ_ENABLED=true
    services=(
      big-market-market-service
      big-market-message-job-service
      big-market-fulfillment-service
      big-market-rebate-service
      big-market-strategy-service
    )
    ;;
esac

if [ "$secure" = true ]; then
  secure_services=(
    big-market-gateway
    big-market-auth-service
    big-market-admin-service
    big-market-market-service
    big-market-chatbot-service
    big-market-message-job-service
    big-market-account-service
  )
  case "$mode" in
    fulfillment)
      secure_services+=(big-market-fulfillment-service)
      ;;
    rebate-strategy)
      secure_services+=(big-market-rebate-service big-market-strategy-service)
      ;;
    all)
      secure_services+=(
        big-market-fulfillment-service
        big-market-rebate-service
        big-market-strategy-service
      )
      ;;
  esac
  services=("${secure_services[@]}")
fi

echo "Starting provider mode '$mode' (secure=$secure); recreating: ${services[*]}"
docker compose "${compose_files[@]}" up --build --force-recreate -d "${services[@]}"

#!/usr/bin/env bash
# Start Rust stack with non-default credentials (secure learning profile).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"

export BM_SECURE=1
export JWT_SECRET="${JWT_SECRET:-rust-local-secure-jwt-secret-32b}"
export BM_JWT_SECRET="${BM_JWT_SECRET:-$JWT_SECRET}"
export BM_GW_JWT_SECRET="${BM_GW_JWT_SECRET:-$JWT_SECRET}"
export BM_INTERNAL_TOKEN="${BM_INTERNAL_TOKEN:-${CHAT_INTERNAL_SERVICE_TOKEN:-rust-internal-secure-token}}"
export BM_DEV_USERS="${BM_DEV_USERS:-${APP_AUTH_DEV_USERS:-xiaofuge:SecureDemo1!,admin:SecureAdmin1!}}"
export DEMO_USER_ID="${DEMO_USER_ID:-xiaofuge}"
export DEMO_USER_PASSWORD="${DEMO_USER_PASSWORD:-SecureDemo1!}"
export DEMO_ADMIN_USER_ID="${DEMO_ADMIN_USER_ID:-admin}"
export DEMO_ADMIN_PASSWORD="${DEMO_ADMIN_PASSWORD:-SecureAdmin1!}"

"$ROOT/scripts/run-rust-stack.sh"
echo "Rust secure stack ready (BM_SECURE=1, non-default JWT/internal token)"

#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
echo "NOTE: Java chat E2E retired; Rust API smoke covers chat deduct/refund" >&2
exec "$ROOT/scripts/smoke-rust-api.sh" "$@"

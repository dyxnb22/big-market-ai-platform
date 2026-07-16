#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
echo "NOTE: Java raffle E2E retired; use acceptance-rust.sh (API smoke covers closed loop)" >&2
exec "$ROOT/scripts/smoke-rust-api.sh" "$@"

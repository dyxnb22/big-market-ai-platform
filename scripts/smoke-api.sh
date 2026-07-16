#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
echo "NOTE: forwarding to smoke-rust-api.sh" >&2
exec "$ROOT/scripts/smoke-rust-api.sh" "$@"

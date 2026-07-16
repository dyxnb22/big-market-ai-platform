#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
echo "NOTE: forwarding to smoke-rust-security.sh" >&2
exec "$ROOT/scripts/smoke-rust-security.sh" "$@"

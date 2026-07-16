#!/usr/bin/env bash
# Compatibility entrypoint — Java acceptance was retired with the Spring stack.
# Delegates to the Rust acceptance suite.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
echo "NOTE: Java stack removed; forwarding to ./scripts/acceptance-rust.sh $*" >&2
exec "$ROOT/scripts/acceptance-rust.sh" "$@"

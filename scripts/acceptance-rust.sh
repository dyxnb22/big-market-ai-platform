#!/usr/bin/env bash
# Rust acceptance (memory / local binaries). Does not require Java or Docker.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
RUN_E2E=false
for arg in "$@"; do
  case "$arg" in
    --e2e|--playwright) RUN_E2E=true ;;
    --help|-h)
      echo "Usage: $0 [--e2e]"
      echo "  --e2e  Also run Playwright against Rust stack (requires Node + Chromium)"
      exit 0
      ;;
  esac
done

cd "$ROOT/big-market-rs"
echo "=== cargo test ==="
cargo test --workspace --quiet
echo "=== clippy ==="
cargo clippy --workspace -- -D warnings
echo "=== API smoke ==="
"$ROOT/scripts/smoke-rust-api.sh"
if [[ "$RUN_E2E" == true ]]; then
  echo "=== Playwright E2E ==="
  "$ROOT/scripts/acceptance-rust-e2e.sh"
fi
echo
echo "acceptance-rust PASS"

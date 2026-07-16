#!/usr/bin/env bash
# Rust acceptance (memory / local binaries). Does not require Java or Docker.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
RUN_E2E=false
RUN_SECURE=false
RUN_MYSQL=false
for arg in "$@"; do
  case "$arg" in
    --e2e|--playwright) RUN_E2E=true ;;
    --secure) RUN_SECURE=true ;;
    --mysql) RUN_MYSQL=true ;;
    --help|-h)
      echo "Usage: $0 [--e2e] [--secure] [--mysql]"
      echo "  --e2e     Also run Playwright against Rust stack"
      echo "  --secure  Run smoke-rust-security (use run-rust-secure.sh first)"
      echo "  --mysql   Run smoke-rust-mysql when MySQL :13306 is up"
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
if [[ "$RUN_SECURE" == true ]]; then
  echo "=== Rust security smoke ==="
  "$ROOT/scripts/smoke-rust-security.sh"
fi
if [[ "$RUN_MYSQL" == true ]]; then
  echo "=== Rust MySQL smoke ==="
  "$ROOT/scripts/smoke-rust-mysql.sh"
fi
if [[ "$RUN_E2E" == true ]]; then
  echo "=== Playwright E2E ==="
  "$ROOT/scripts/acceptance-rust-e2e.sh"
fi
echo
echo "acceptance-rust PASS"

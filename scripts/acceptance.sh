#!/usr/bin/env bash
# Full local acceptance for Big Market (Rust).
# Default: cargo test + clippy + API smoke (interview baseline).
# Optional: --strategy --secure --mysql --rabbit --e2e
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
RUN_E2E=false
RUN_SECURE=false
RUN_MYSQL=false
RUN_RABBIT=false
RUN_STRATEGY=false
for arg in "$@"; do
  case "$arg" in
    --e2e|--playwright) RUN_E2E=true ;;
    --secure) RUN_SECURE=true ;;
    --mysql) RUN_MYSQL=true ;;
    --rabbit) RUN_RABBIT=true ;;
    --strategy) RUN_STRATEGY=true ;;
    --help|-h)
      echo "Usage: $0 [--e2e] [--secure] [--mysql] [--rabbit] [--strategy]"
      echo "  (default) cargo test + clippy + API smoke"
      echo "  --strategy  lock-demo activity + BM_STRATEGY_CHAIN blacklist"
      echo "  --secure    JWT revoke / internal token / secure boot"
      echo "  --mysql     MySQL backend smoke"
      echo "  --rabbit    Rabbit + bm-worker smoke"
      echo "  --e2e       Playwright UI tests"
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
"$ROOT/scripts/smoke-api.sh"
if [[ "$RUN_STRATEGY" == true ]]; then
  echo "=== strategy smoke ==="
  "$ROOT/scripts/smoke-strategy.sh"
fi
if [[ "$RUN_SECURE" == true ]]; then
  echo "=== security smoke ==="
  "$ROOT/scripts/smoke-security.sh"
fi
if [[ "$RUN_MYSQL" == true ]]; then
  echo "=== MySQL smoke ==="
  "$ROOT/scripts/smoke-mysql.sh"
fi
if [[ "$RUN_RABBIT" == true ]]; then
  echo "=== Rabbit smoke ==="
  "$ROOT/scripts/smoke-rabbit.sh"
fi
if [[ "$RUN_E2E" == true ]]; then
  echo "=== Playwright E2E ==="
  "$ROOT/scripts/acceptance-e2e.sh"
fi
echo
echo "acceptance PASS"

#!/usr/bin/env bash
# Rust acceptance (memory / local binaries). Does not require Java or Docker.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT/big-market-rs"
echo "=== cargo test ==="
cargo test --workspace --quiet
echo "=== clippy ==="
cargo clippy --workspace -- -D warnings
echo "=== API smoke ==="
"$ROOT/scripts/smoke-rust-api.sh"
echo
echo "acceptance-rust PASS"

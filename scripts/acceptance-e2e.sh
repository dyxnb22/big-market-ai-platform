#!/usr/bin/env bash
# Playwright E2E against stack (gateway :8080 + web :5173).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

export E2E_BASE_URL="${E2E_BASE_URL:-http://127.0.0.1:5173}"
export BM_DATA_DIR="${BM_DATA_DIR:-$ROOT/big-market-rs/target/e2e-data}"
export BM_DEV_SLOW_DRAW_MS="${BM_DEV_SLOW_DRAW_MS:-300}"

echo "=== E2E setup ==="
"$ROOT/scripts/run-stack.sh"
"$ROOT/scripts/web-serve.sh"

if [[ ! -d node_modules ]]; then
  npm install --no-audit --no-fund
fi

if [[ -z "${PLAYWRIGHT_BROWSERS_PATH:-}" ]] && [[ -d "$ROOT/.playwright-browsers" ]]; then
  export PLAYWRIGHT_BROWSERS_PATH="$ROOT/.playwright-browsers"
fi

if ! npx playwright install --dry-run chromium 2>/dev/null | grep -q "is installed"; then
  npx playwright install chromium
fi

count_tests() {
  npx playwright test --list 2>/dev/null \
    | awk '/^Total: [0-9]+ tests? / { print $2; found=1 } END { if (!found) print 0 }'
}

listed="$(count_tests)"
if [[ "$listed" == "0" ]]; then
  echo "FAIL: Playwright listed zero tests" >&2
  exit 1
fi
echo "Playwright listed ${listed} tests"

echo "=== Playwright run 1 ==="
npx playwright test --workers=1
echo "=== Playwright run 2 ==="
npx playwright test --workers=1

if [[ ! -d playwright-report ]] && [[ ! -d test-results ]]; then
  echo "FAIL: missing playwright-report/ or test-results/" >&2
  exit 1
fi

echo
echo "acceptance-e2e PASS (${listed} tests x2)"

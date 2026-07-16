#!/usr/bin/env bash
# Java stack removed — dual compare runs Rust-only unless JAVA_API_BASE is set externally.
# Compare Rust vs Java API contracts when both stacks are reachable.
# Rust default: http://127.0.0.1:8080/api/v1
# Java legacy (optional): JAVA_API_BASE e.g. http://127.0.0.1:8098/api/v1
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
RUST_API="${RUST_API:-http://127.0.0.1:8080/api/v1}"
JAVA_API="${JAVA_API_BASE:-}"

pass() { echo "  PASS  $*"; }
skip() { echo "  SKIP  $*"; }
fail() { echo "  FAIL  $*" >&2; exit 1; }

json_field() {
  python3 -c "import json,sys; d=json.load(sys.stdin); print($1)"
}

echo "=== Dual-stack contract check ==="

if ! curl -fsS "${RUST_API%/api/v1}/health" >/dev/null 2>&1; then
  "$ROOT/scripts/run-rust-stack.sh"
fi

RUST_STAGE="$(curl -fsS "$RUST_API/raffle/activity/query_stage_activity_id?channel=c01&source=s01")"
RUST_ACT="$(printf '%s' "$RUST_STAGE" | json_field "d['data']")"
[ "$RUST_ACT" = "100401" ] || fail "rust stage: $RUST_STAGE"
pass "rust stage activity 100401"

RUST_DISPLAY="$(curl -fsS "$RUST_API/admin/config/public/display?activityId=100401")"
RUST_STATE="$(printf '%s' "$RUST_DISPLAY" | json_field "d['data']['state']")"
[ "$RUST_STATE" = "online" ] || fail "rust display: $RUST_DISPLAY"
pass "rust public display online"

if [ -z "$JAVA_API" ]; then
  skip "JAVA_API_BASE unset — rust-only contract check done"
  echo
  echo "Dual-stack check passed (rust-only)."
  exit 0
fi

if ! curl -fsS "${JAVA_API%/api/v1}/actuator/health" >/dev/null 2>&1 \
  && ! curl -fsS "${JAVA_API%/api/v1}/health" >/dev/null 2>&1; then
  skip "Java API not reachable at $JAVA_API"
  echo
  echo "Dual-stack check passed (rust-only; java down)."
  exit 0
fi

JAVA_STAGE="$(curl -fsS "$JAVA_API/raffle/activity/query_stage_activity_id?channel=c01&source=s01")"
JAVA_ACT="$(printf '%s' "$JAVA_STAGE" | json_field "d['data']")"
[ "$JAVA_ACT" = "$RUST_ACT" ] || fail "stage mismatch rust=$RUST_ACT java=$JAVA_ACT"
pass "java stage matches rust ($JAVA_ACT)"

JAVA_DISPLAY="$(curl -fsS "$JAVA_API/admin/config/public/display?activityId=100401")"
JAVA_STATE="$(printf '%s' "$JAVA_DISPLAY" | json_field "d['data']['state']")"
[ "$JAVA_STATE" = "$RUST_STATE" ] || fail "display state mismatch rust=$RUST_STATE java=$JAVA_STATE"
pass "java display state matches rust"

echo
echo "Dual-stack contract check passed."

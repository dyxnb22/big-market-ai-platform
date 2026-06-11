#!/usr/bin/env bash
# validate-microservices-phase-3-rebate-completion.sh
# Meta validator: runs all Phase 3 rebate-service validators in sequence.
# All validators must pass for Phase 3 to be considered repo-ready.

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
TOTAL_PASS=0
TOTAL_FAIL=0

run_validator() {
  local script="$1"
  local label="$2"
  echo ""
  echo "###################################################################"
  echo "  Running: $label"
  echo "  Script:  $script"
  echo "###################################################################"
  if bash "$SCRIPT_DIR/$script"; then
    TOTAL_PASS=$((TOTAL_PASS + 1))
    echo "  => $label: PASS"
  else
    TOTAL_FAIL=$((TOTAL_FAIL + 1))
    echo "  => $label: FAIL"
  fi
}

echo ""
echo "========================================================================"
echo "  Phase 3 Rebate-Service Completion Meta Validator"
echo "  $(date)"
echo "========================================================================"

run_validator "validate-microservices-phase-3-next-extraction.sh"         "Phase 3 module boundary + wiring"
run_validator "validate-microservices-phase-3-rebate-adapter.sh"          "Phase 3 write adapter boundary"
run_validator "validate-microservices-phase-3-rebate-provider-ownership.sh" "Phase 3 legacy provider gate"
run_validator "validate-microservices-phase-3-rebate-read-adapter.sh"     "Phase 3-A/B read adapter boundary"
run_validator "validate-microservices-phase-3-rebate-dependency-narrowing.sh" "Phase 3-C dependency narrowing audit"
run_validator "validate-microservices-phase-3-rebate-cutover-readiness.sh" "Phase 3-E cutover rehearsal (dry-run)"

echo ""
echo "========================================================================"
echo "  META SUMMARY"
echo "========================================================================"
echo "Validators passed: $TOTAL_PASS"
echo "Validators failed: $TOTAL_FAIL"

if [ "$TOTAL_FAIL" -eq 0 ]; then
  echo "RESULT: PASS — Phase 3 repo-ready. Traffic cutover is Phase 8 work."
  exit 0
else
  echo "RESULT: FAIL — $TOTAL_FAIL validator(s) failed. Fix before tagging repo-ready."
  exit 1
fi

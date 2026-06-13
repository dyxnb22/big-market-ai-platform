#!/usr/bin/env bash
# Repo-only aggregate validator for microservices split gates.
#
# Deterministic, fail-fast, and deliberately avoids Docker, DB, MQ, and remote
# commands. Each child script is expected to be static/read-only.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"

GATES=(
  "scripts/validate-microservices-master-plan.sh"
  "scripts/validate-microservices-phase-6-dao-ownership-matrix.sh"
  "scripts/validate-microservices-phase-6-package-ownership-boundaries.sh"
  "scripts/validate-microservices-phase-7-strategy-activity-mapping-boundary.sh"
  "scripts/validate-microservices-phase-7-account-boundary-prep.sh"
  "scripts/validate-microservices-phase-7-award-activity-order-boundary.sh"
  "scripts/validate-microservices-phase-7-award-credit-outbox-boundary.sh"
  "scripts/validate-microservices-phase-7-credit-award-task-job-boundary.sh"
  "scripts/validate-microservices-phase-7-task-outbox-proposed-ddl.sh"
  "scripts/validate-microservices-phase-7-task-outbox-port-boundaries.sh"
  "scripts/validate-microservices-phase-7-task-outbox-ownership.sh"
  "scripts/validate-microservices-phase-7-db-isolation-plan.sh"
  "scripts/validate-microservices-service-module-ownership.sh"
  "scripts/validate-microservices-production-flag-matrix.sh"
  "scripts/validate-microservices-phase-8-cutover-readiness.sh"
  "scripts/validate-microservices-phase-8-cutover-evidence-pack.sh"
  "scripts/validate-microservices-phase-8-staging-evidence-intake.sh"
  "scripts/validate-microservices-phase-8-staging-evidence-consistency.sh"
  "scripts/validate-microservices-phase-8-external-evidence-intake.sh"
  "scripts/validate-microservices-legacy-cleanup-readiness.sh"
  "scripts/validate-microservices-post-cutover-cleanup-gates.sh"
  "scripts/validate-microservices-learning-mode-closure.sh"
  "scripts/validate-microservices-phase-8-runtime-safety.sh"
  "scripts/validate-microservices-phase-8-cutover-conflict-matrix.sh"
  "scripts/validate-microservices-phase-8-idempotency-rollback-matrix.sh"
  "scripts/validate-microservices-phase-8-external-evidence-readiness-pack.sh"
)

echo ""
echo "========================================================================"
echo "  All Microservices Split Repo-Only Gates"
echo "  Repo: $REPO_ROOT"
echo "========================================================================"

completed=0
total=${#GATES[@]}

for gate in "${GATES[@]}"; do
  path="$REPO_ROOT/$gate"
  echo ""
  echo "[$((completed + 1))/$total] $gate"
  if [[ ! -x "$path" ]]; then
    echo "[FAIL] gate is missing or not executable: $gate"
    echo ""
    echo "RESULT: FAILED after $completed/$total gate(s)"
    exit 1
  fi
  "$path"
  completed=$((completed + 1))
done

echo ""
echo "========================================================================"
echo "RESULT: ALL CHECKS PASSED - $completed/$total repo-only split gates green"
echo "========================================================================"

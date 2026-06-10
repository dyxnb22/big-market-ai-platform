#!/usr/bin/env bash
# validate-b17-evidence-consistency.sh — Phase 2.2-B21
#
# Local/static guard for dated B17 evidence files. It compares each evidence
# file's declared B17 pre-flight PASS count with the current dry-run output of
# scripts/execute-account-service-staging-b17.sh.
#
# Usage:
#   ./scripts/validate-b17-evidence-consistency.sh
#   ./scripts/validate-b17-evidence-consistency.sh docs/evidence/b17-staging-evidence-20260610.md
#
# Safety:
#   - No DB connections
#   - No staging/prod writes
#   - Does not modify evidence files
set -euo pipefail

B17_SCRIPT="scripts/execute-account-service-staging-b17.sh"

PASS=0
FAIL=0

ok()   { echo "[PASS] $*"; PASS=$((PASS + 1)); }
fail() { echo "[FAIL] $*"; FAIL=$((FAIL + 1)); }
info() { echo "[INFO] $*"; }

info "=== Phase 2.2-B21 B17 Evidence Consistency Guard ==="
echo ""

if [[ ! -x "$B17_SCRIPT" ]]; then
    fail "$B17_SCRIPT missing or not executable"
else
    ok "$B17_SCRIPT exists and is executable"
fi

B17_OUT=$("./$B17_SCRIPT" 2>&1) || true
B17_PASS=$(echo "$B17_OUT" | awk -F': *' '/^PASS:/ {print $2}' | tail -1)
B17_FAIL=$(echo "$B17_OUT" | awk -F': *' '/^FAIL:/ {print $2}' | tail -1)
B17_PASS="${B17_PASS:-0}"
B17_FAIL="${B17_FAIL:-0}"

if [[ "$B17_FAIL" == "0" && "$B17_PASS" =~ ^[0-9]+$ && "$B17_PASS" -gt 0 ]]; then
    ok "B17 dry-run summary is ${B17_PASS} PASS, 0 FAIL"
else
    fail "B17 dry-run summary is ${B17_PASS} PASS, ${B17_FAIL} FAIL"
fi

if [[ "$#" -gt 0 ]]; then
    EVIDENCE_FILES=("$@")
else
    EVIDENCE_FILES=(docs/evidence/b17-staging-evidence-*.md)
fi

FOUND=0
for evidence_file in "${EVIDENCE_FILES[@]}"; do
    if [[ ! -e "$evidence_file" ]]; then
        continue
    fi
    FOUND=$((FOUND + 1))
    info "Checking $evidence_file"

    if [[ ! -f "$evidence_file" ]]; then
        fail "$evidence_file is not a regular file"
        continue
    fi

    declared_line=$(grep -E "^### B17 Pre-Flight Gate: [0-9]+/[0-9]+ PASS" "$evidence_file" || true)
    if [[ -z "$declared_line" ]]; then
        fail "$evidence_file missing '### B17 Pre-Flight Gate: N/N PASS' heading"
    else
        declared_count=$(echo "$declared_line" | sed -E 's/.*: ([0-9]+)\/([0-9]+) PASS.*/\1 \2/')
        declared_pass=$(echo "$declared_count" | awk '{print $1}')
        declared_total=$(echo "$declared_count" | awk '{print $2}')

        if [[ "$declared_pass" == "$B17_PASS" && "$declared_total" == "$B17_PASS" ]]; then
            ok "$evidence_file declares B17 pre-flight ${declared_pass}/${declared_total} PASS, matching script output"
        else
            fail "$evidence_file declares B17 pre-flight ${declared_pass}/${declared_total} PASS, but script reports ${B17_PASS}/${B17_PASS}"
        fi
    fi

    if awk '/^### B17 Pre-Flight Gate:/,/^### /' "$evidence_file" \
            | grep -q "Evidence file written"; then
        fail "$evidence_file counts evidence-file materialization inside the B17 pre-flight gate"
    else
        ok "$evidence_file keeps evidence-file materialization separate from the B17 pre-flight gate"
    fi

    if grep -q "B17 Evidence Consistency" "$evidence_file"; then
        ok "$evidence_file records the B21 consistency guard result"
    else
        fail "$evidence_file missing B21 consistency guard result"
    fi
done

if [[ "$FOUND" -eq 0 ]]; then
    fail "No B17 dated evidence files found"
fi

echo ""
echo "=== B17 Evidence Consistency Summary ==="
echo "PASS: $PASS"
echo "FAIL: $FAIL"

if [[ "$FAIL" -eq 0 ]]; then
    echo "[OK] B17 evidence PASS counts match the script output."
    exit 0
else
    echo "[FAIL] $FAIL consistency issue(s) found."
    exit 1
fi

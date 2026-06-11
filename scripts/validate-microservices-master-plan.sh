#!/usr/bin/env bash
# validate-microservices-master-plan.sh
#
# Deterministic, repo-only validator for the microservices decomposition master plan.
# Checks only that the plan document exists and contains the required structural
# headings, the boundary matrix, the current execution order, the non-goals
# section, and the safety rules recap. Does not execute Java, DDL, or network.

set -u

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
PLAN="$ROOT_DIR/docs/microservices-decomposition-master-plan.md"

pass=0
fail=0

check() {
    local description="$1"
    local pattern="$2"
    if grep -qE "$pattern" "$PLAN"; then
        echo "PASS  $description"
        pass=$((pass + 1))
    else
        echo "FAIL  $description (missing pattern: $pattern)"
        fail=$((fail + 1))
    fi
}

if [ ! -f "$PLAN" ]; then
    echo "FAIL  master plan document missing: $PLAN"
    exit 1
fi

echo "== Section 1: required headings =="
check "Executive Summary heading"        "^## 1\. Executive Summary"
check "Current State Inventory heading"  "^## 2\. Current State Inventory"
check "Definition of Done heading"       "^## 3\. Definition of Done"
check "Phase Plan heading"               "^## 4\. Phase Plan"
check "Batch Backlog heading"            "^## 5\. Batch Backlog"
check "Boundary Matrix heading"          "^## 6\. Boundary Matrix"
check "Dependency Rules heading"         "^## 7\. Dependency Rules"
check "Recommended Execution Order"      "^## 8\. Recommended Execution Order"
check "Risk Register heading"            "^## 9\. Risk Register"
check "Non-Goals heading"                "^## 10\. Non-Goals"
check "Safety Rules heading"             "^## 11\. Safety Rules"

echo
echo "== Section 2: required phase coverage =="
check "Phase 3 covered"                  "Phase 3"
check "Phase 4 covered"                  "Phase 4"
check "Phase 5 covered"                  "Phase 5"
check "Phase 6 covered"                  "Phase 6"
check "Phase 7 covered"                  "Phase 7"
check "Phase 8 covered"                  "Phase 8"

echo
echo "== Section 3: boundary matrix rows =="
check "boundary row: account / credit"   "account / credit"
check "boundary row: account / quota"    "account / quota"
check "boundary row: fulfillment"        "fulfillment / award"
check "boundary row: rebate"             "\| rebate \|"
check "boundary row: strategy"           "\| strategy \|"
check "boundary row: activity / draw"    "activity / draw"
check "boundary row: task / outbox"      "task / outbox"
check "boundary row: auth"               "\| auth \|"
check "boundary row: admin / config"     "admin / config"
check "boundary row: chatbot"            "\| chatbot \|"
check "boundary row: query / search"     "query / search"

echo
echo "== Section 4: current execution order present =="
check "phase 3 through 7 repo-complete"  "Phase 3 through Phase 7 are repo-complete"
check "phase 8 external-gated"           "Phase 8 repo readiness is complete"
check "execution batch 1 aggregate gate" '^\| 1 \| Keep `scripts/validate-microservices-split-all-gates\.sh` green in CI'
check "execution batch 2 service ownership" "^\| 2 \| Extend service ownership validators"
check "execution batch 3 external evidence" "^\| 3 \| Prepare external evidence files"
check "execution batch 4 legacy providers" "^\| 4 \| After external 7-day stability gates"
check "execution batch 5 obsolete paths" "^\| 5 \| After external 30-day stability gates"
check "completion index linked"          "docs/microservices-split-completion-index\.md"

echo
echo "== Section 5: non-goals enumerated =="
check "non-goal: no big-bang rewrite"    "[Nn]o big-bang rewrite"
check "non-goal: no immediate activity"  "[Nn]o immediate activity-service extraction"
check "non-goal: no production traffic"  "[Nn]o production traffic enablement"
check "non-goal: no evidence expansion"  "[Nn]o expansion of generated evidence"
check "non-goal: no large renames"       "[Nn]o large-scale package renames"

echo
echo "== Section 6: safety rules recap =="
check "safety rule: no DBs/MQ"           "[Nn]o connection to staging"
check "safety rule: no mysql/docker"     "mysql.*docker.*curl"
check "safety rule: no traffic"          "[Nn]o traffic enablement"
check "safety rule: no java behavior"    "[Nn]o Java behavior change"
check "safety rule: no remote flag flip" "[Nn]o remote or dangerous flag default flipped"

echo
echo "Summary: $pass PASS, $fail FAIL"

if [ "$fail" -gt 0 ]; then
    exit 1
fi
exit 0

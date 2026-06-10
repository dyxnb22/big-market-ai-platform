# Phase 2.2-B21 Evidence Consistency Hardening

**Date:** 2026-06-10
**Scope:** Local documentation and validation hardening only.
**Remote writes:** None.
**Runtime flag changes:** None. `remote-quota-decrement` remains default-false.

## What Changed

- Corrected `docs/evidence/b17-staging-evidence-20260610.md` so the B17 pre-flight gate reports `6/6 PASS`, matching `./scripts/execute-account-service-staging-b17.sh`.
- Kept evidence-file materialization as a separate local evidence note, not a B17 pre-flight check.
- Added `scripts/validate-b17-evidence-consistency.sh` to compare dated B17 evidence files against the current B17 dry-run PASS count.
- Updated B18 staging-evidence guidance so operators validate dated B17 evidence files, not the blank B17 template.

## Validation Commands

```bash
./scripts/validate-b17-evidence-consistency.sh docs/evidence/b17-staging-evidence-20260610.md
./scripts/execute-account-service-staging-b17.sh
./scripts/validate-account-service-cutover-b20.sh
./scripts/validate-account-service-production-b18.sh
git diff --check
```

## Remaining Manual Blockers

These remain **PENDING** because this batch did not use staging DB credentials or XXL-Job UI access:

1. Apply `docs/sql/proposed-quota-decrement-ledger.sql` to staging `big_market_01` and `big_market_02`.
2. Apply `docs/sql/proposed-credit-award-task-outbox.sql` to staging `big_market_01` and `big_market_02`.
3. Register `DispatchCreditAwardTaskJob_DB1` and `DispatchCreditAwardTaskJob_DB2` in staging XXL-Job admin.

# Phase 2 DBA DDL Execution Evidence — Intake Template

**Status:** TEMPLATE — fill in as each DDL action is completed
**Owner:** DBA
**Last updated:** ___

> **THIS TEMPLATE IS NOT AN APPROVAL AND DOES NOT ENABLE TRAFFIC.**
> All three dangerous flags must remain `false` unless explicitly authorized by the oncall lead.
> Do not rollback DDL (DROP TABLE) without written approval from the incident lead.
>
> **Who fills this:** DBA only.
> **Who reviews it:** Oncall lead (required before any staging E2E / before production gate Phase C).
> **Generated evidence** (screenshots, SQL output) must be stored locally or in a secure artifact store.
> Files in `docs/evidence/generated/` are gitignored and must never be committed to this repo.

---

## Dangerous Flag Safety

The following flags must remain `false` in all config files at all times during DDL operations.
The DBA does not enable application flags. Flag enabling is the Engineer's responsibility.

| Flag | Hard Rule |
|------|-----------|
| `account.award-credit-outbox.enabled` | Never enable without DBA DDL confirmation + unique-key verification |
| `account.fulfillment.remote-award.enabled` | Never enable before outbox flag is stable and B23-C staging evidence signed |
| `account.service.remote-quota-decrement.enabled` | Phase 2.2 separate gate — not part of DBA scope here |

---

## DBA Staging DDL Evidence

**Gate:** All items in this section must be SIGNED before the Engineer runs B17/B23-C E2E.
**Prerequisite docs:** [`phase-2-dba-checklist.md`](phase-2-dba-checklist.md)

### Staging Ledger DDL (Phase 2.2-B17)

| # | Evidence | Screenshot / Output Ref | Applied By | Timestamp | Result |
|---|----------|------------------------|-----------|-----------|--------|
| DA1 | Staging `big_market_01` ledger DDL apply result | ___ | ___ | ___ | SUCCESS / ERROR |
| DA2 | Staging `big_market_02` ledger DDL apply result | ___ | ___ | ___ | SUCCESS / ERROR |
| DA3 | Staging `big_market_01` outbox DDL apply result | ___ | ___ | ___ | SUCCESS / ERROR |
| DA4 | Staging `big_market_02` outbox DDL apply result | ___ | ___ | ___ | SUCCESS / ERROR |
| DA5 | `SHOW TABLES LIKE 'raffle_quota_decrement_ledger%'` (both DBs, 4 tables each) | ___ | ___ | ___ | CONFIRMED / FAIL |
| DA6 | `SHOW TABLES LIKE 'credit_award_task%'` (both DBs, 4 tables each) | ___ | ___ | ___ | CONFIRMED / FAIL |
| DA7 | `SHOW INDEX` confirming `uq_award_order_id` on all 8 staging outbox shards | ___ | ___ | ___ | CONFIRMED / FAIL |
| DA8 | `SHOW INDEX` confirming `uq_out_business_no` on all staging `user_credit_order_*` shards | ___ | ___ | ___ | CONFIRMED / FAIL |
| DA9 | `SHOW INDEX` confirming `uq_user_activity_biz` on all staging ledger shards | ___ | ___ | ___ | CONFIRMED / FAIL |

**Staging DDL Gate — All DA1–DA9 complete:** YES / NO

> **NO-GO:** If any row is ERROR or FAIL, do NOT sign off. Do NOT allow the Engineer to enable any flag.
> Escalate to oncall lead immediately.

**DBA Staging Sign-Off:**

| Name | Timestamp | Decision |
|------|-----------|---------|
| ___ | ___ | SIGNED / REFUSED |

---

## DBA Production DDL Evidence

**Gate:** All items in this section must be SIGNED before the B23-E cutover window (P5).
**Prerequisites:** B23-C staging evidence SE1–SE11 signed + B23-D Phase B gate PASS.
**Prerequisite docs:** [`phase-2-dba-checklist.md`](phase-2-dba-checklist.md)

> Apply production DDL only after B23-C staging evidence is signed off and B23-D Phase B is passed.
> Only `credit_award_task` outbox DDL applies to production — ledger DDL is Phase 2.2 staging only.

### Production Outbox DDL (Phase 2.3-D)

| # | Evidence | Screenshot / Output Ref | Applied By | Timestamp | Result |
|---|----------|------------------------|-----------|-----------|--------|
| DA10 | Production `big_market_01` outbox DDL apply result | ___ | ___ | ___ | SUCCESS / ERROR |
| DA11 | Production `big_market_02` outbox DDL apply result | ___ | ___ | ___ | SUCCESS / ERROR |
| DA12 | `SHOW TABLES LIKE 'credit_award_task%'` both prod DBs (4 tables each) | ___ | ___ | ___ | CONFIRMED / FAIL |
| DA13 | `SHOW INDEX` confirming `uq_award_order_id` on all 8 production outbox shards | ___ | ___ | ___ | CONFIRMED / FAIL |
| DA14 | `SHOW INDEX` confirming `uq_out_business_no` on all production `user_credit_order_*` shards | ___ | ___ | ___ | CONFIRMED / FAIL |

**Production DDL Gate — All DA10–DA14 complete:** YES / NO

> **NO-GO:** If any row is ERROR or FAIL, do NOT sign off.
> Do NOT allow Engineer to proceed to P5 (outbox flag enable). Escalate to oncall lead immediately.

**DBA Production Sign-Off:**

| Name | Timestamp | Decision |
|------|-----------|---------|
| ___ | ___ | SIGNED / REFUSED |

---

## B23-E Cutover Approval Prerequisites (DBA view)

Before the B23-E cutover window opens, the DBA must confirm all of the following:

| Check | Status |
|-------|--------|
| Staging DDL Gate complete (DA1–DA9, all SIGNED) | PENDING / DONE |
| Production DDL Gate complete (DA10–DA14, all SIGNED) | PENDING / DONE |
| No DDL rollback (DROP TABLE) pending or in-flight | PENDING / CONFIRMED |
| Oncall lead has reviewed both DBA sign-offs | PENDING / CONFIRMED |

> **Hard gate:** The B23-E cutover execution (`phase-2-3-e-fulfillment-cutover-execution.md`) must NOT start
> until all rows above are DONE or CONFIRMED.

---

## NO-GO Rules for DBA

Stop and escalate immediately to the oncall lead if ANY of the following:

1. Any shard DB returns ERROR on DDL apply.
2. `uq_award_order_id` missing from any `credit_award_task` shard.
3. `uq_out_business_no` missing from any `user_credit_order_*` shard.
4. Table count ≠ 4 for `credit_award_task%` in either DB.
5. Any staging or production table reports data loss after DDL apply.
6. Any attempt to DROP a table without written approval from the incident lead.

---

## Completion Status

<!-- Operator: update Status values as evidence is collected.
     Machine-readable by validate-phase-2-external-evidence-completion.sh
     Valid values — TODO: not started | PASS: complete | FAIL: failed (triggers NO-GO) | GO: gate approved | NO-GO: gate refused | PENDING: waiting -->

| Check | Status |
|-------|--------|
| Staging DDL Gate (DA1–DA9) | TODO |
| Production DDL Gate (DA10–DA14) | TODO |
| DBA Staging Sign-Off | TODO |
| DBA Production Sign-Off | TODO |
| B23-E Gate Decision | PENDING |

---

## Evidence Storage Note

Screenshots, SQL outputs, and terminal logs must be stored in your team's secure artifact store
or in the local `docs/evidence/generated/` directory. The `generated/` directory is listed in
`.gitignore` and is local-only — evidence in that directory is never committed to this repo.
Do not commit screenshots or terminal output to the repository.

See: `scripts/collect-phase-2-external-evidence.sh` for the local evidence snapshot script.
See: `scripts/validate-phase-2-external-evidence-completion.sh` for the completion gate validator.
See: `docs/evidence/phase-2-external-execution-pack.md` for the full execution pack.
See: `docs/evidence/phase-2-external-readiness-dashboard.md` for the current readiness dashboard.

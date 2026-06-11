# Microservices Learning-Mode Closure — Opus Review

Review date: 2026-06-11.
Reviewer model: Claude Opus 4.7 (xhigh effort).
Scope: post-closure audit of the local Docker learning project; explicitly not a
real production readiness review.

## Git State at Review Start

- Working tree: clean (`git status` reported no uncommitted changes).
- HEAD: `cfc32084858fd1704ee214635c2ed7d13b2a0242` (`docs: polish learning-mode closure`).
- Tag `microservices-learning-mode-complete` target: `b15b458a7fd109bd4de58101b64aeb6a70355e43`
  (`docs: complete learning-mode microservices closure`).
- HEAD is one commit ahead of the tag. The polish commit only fixes two
  validator hygiene issues introduced by the closure commit itself:
  - replaced an SQL-style `-- ALTER TABLE ...` comment in
    `big-market-fulfillment-service/.../task_mapper.xml` with an XML comment so
    the phase-7-B DDL grep no longer matches it;
  - tightened the phase-7-B mapper-change guard to use
    `git diff --name-only --diff-filter=M` so first-time mapper additions in
    new service modules are not flagged as modifications.
- The tag still points at a commit at which the aggregate gate
  `validate-microservices-split-all-gates.sh` reports two failures. Any consumer
  who checks out the tag literally would see those failures until cfc3208 is
  applied. See Finding H-1 below.

## Files Reviewed

Documentation:
- `docs/microservices-learning-mode-closure.md`
- `docs/evidence/phase-8-local-learning-cutover-evidence.md`
- `docs/microservices-split-completion-index.md`
- `docs/microservices-next-execution-roadmap.md`
- `docs/microservices-phase-8-cutover-runbook.md`
- `docs/microservices-phase-8-external-evidence-intake.md`
- `docs/evidence/phase-8-go-no-go-checklist.md`
- `docs/microservices-legacy-cleanup-inventory.md`
- `docs/archive/microservices-historical-docs-index.md`

Validators:
- `scripts/validate-microservices-learning-mode-closure.sh`
- `scripts/validate-microservices-split-all-gates.sh`
- `scripts/validate-microservices-phase-7-task-outbox-ownership.sh`
- `scripts/validate-microservices-service-module-ownership.sh`
- `scripts/validate-microservices-production-flag-matrix.sh`
- `scripts/validate-microservices-legacy-cleanup-readiness.sh`
- `scripts/validate-microservices-post-cutover-cleanup-gates.sh`

Local compatibility wiring (newly added in the closure commit):
- `big-market-account-service/src/main/java/com/dyx/market/account/config/LocalActivityPortConfig.java`
- `big-market-message-job-service/src/main/java/com/dyx/market/message/job/config/LocalActivityPortConfig.java`
- `big-market-market-service/src/main/java/com/dyx/market/market/config/LocalStrategyDecisionConfig.java`
- `big-market-fulfillment-service/src/main/java/com/dyx/market/fulfillment/config/DataSourceConfig.java`
- `big-market-fulfillment-service/src/main/java/com/dyx/market/fulfillment/config/RedisClientConfig.java`
- `big-market-fulfillment-service/src/main/java/com/dyx/market/fulfillment/config/RedisClientConfigProperties.java`
- `big-market-fulfillment-service/src/main/java/com/dyx/market/fulfillment/config/Retrofit2Config.java`
- `big-market-fulfillment-service/src/main/java/com/dyx/market/fulfillment/config/Retrofit2ConfigProperties.java`
- `big-market-fulfillment-service/src/main/resources/mybatis/config/mybatis-config.xml`
- seven new mapper XMLs under
  `big-market-fulfillment-service/src/main/resources/mybatis/mapper/...`.

## Commands Run and Results

| Command | Result |
|---------|--------|
| `./scripts/validate-microservices-learning-mode-closure.sh` | 61/61 PASS |
| `./scripts/validate-microservices-split-all-gates.sh` | 25/25 gates PASS |
| `./scripts/validate-microservices-master-plan.sh` | 48/48 PASS |
| `./scripts/validate-microservices-production-flag-matrix.sh` | 21/21 PASS |
| `./scripts/validate-microservices-legacy-cleanup-readiness.sh` | 80/80 PASS |
| `./scripts/validate-microservices-post-cutover-cleanup-gates.sh` | 61/61 PASS |
| `mvn clean package -DskipTests` | BUILD SUCCESS (26 modules) |
| `docker compose ps` | 8/8 application services healthy |
| `./scripts/validate-microservices-stack.sh --skip-build` | ALL CHECKS PASSED |
| `./scripts/smoke-test-phase-1.sh` | 18/18 PASS |

Search checks:

- `grep -RInE "production[[:space:]]+(ready|complete|approved|signed-off)"`
  on `docs/`, filtered to exclude gated/negation phrases — no matches.
- `grep -RInE "(DBA|Ops|Engineering|Oncall|Product)[[:space:]]+(approved|approval (received|complete))"`
  on `docs/`, filtered to exclude gated/template phrases — no matches.
- `grep -RInE "real (DBA|Ops|Engineering|Oncall|Product)"` on `docs/` —
  only legitimate negations (`does not contain real ...`).
- `grep` for direct cross-boundary DAO imports in `big-market-*-service/src` —
  only MyBatis `<mapper namespace="...">` declarations, which are MyBatis
  routing artifacts and not Java-level DAO imports. No Java file in a service
  module directly couples to a foreign DAO.
- `grep` for accidentally-enabled remote/outbox/cutover flag defaults — no
  matches in service resources or `docker-compose*.yml`.

## Findings

### Critical

None.

### High

**H-1. Tag `microservices-learning-mode-complete` points to a commit whose aggregate validator fails.**

The closure commit `b15b458` introduced two validator regressions that the
follow-up commit `cfc3208` fixes:

- A `-- Recommended index: ALTER TABLE ...` comment inside
  `task_mapper.xml` matched the phase-7-B "non-proposed DDL" grep.
- The phase-7-B mapper change guard treated newly added fulfillment-service
  mapper XMLs as modifications because it used
  `git diff --name-only` without `--diff-filter=M`.

The tag was applied at `b15b458` before the fix landed. Anyone checking out
`microservices-learning-mode-complete` today will see
`validate-microservices-split-all-gates.sh` report `RESULT: 1 CHECK(S) FAILED`,
which contradicts the tag's "complete" promise. This is the only finding that
materially conflicts with the closure narrative.

Per the audit constraints, no tag action was taken. The user-facing
recommendation is documented in the "Tag-vs-HEAD Decision" section below.

### Medium

**M-1. Inventory under-documents which fulfillment mapper copies are learning-mode compatibility versus service-owned.**

The closure commit added seven new mapper XMLs to
`big-market-fulfillment-service/src/main/resources/mybatis/mapper/`:

- mysql: `award_mapper.xml`, `credit_award_task_mapper.xml`, `task_mapper.xml`,
  `user_award_record_mapper.xml`, `user_credit_account_mapper.xml`,
  `user_raffle_order_mapper.xml`;
- elasticsearch: `user_raffle_order_mapper.xml`.

`docs/microservices-legacy-cleanup-inventory.md` currently says:

- `big-market-fulfillment-service` award mapper files are "service-owned and
  protected by service ownership validators";
- `user_credit_account_mapper.xml` and `credit_award_task_mapper.xml` are
  "local learning-mode compatibility copies".

This leaves three mapper files unclassified in the inventory:
`task_mapper.xml`, `user_raffle_order_mapper.xml` (mysql), and
`user_raffle_order_mapper.xml` (elasticsearch). They are functionally learning-
mode compatibility copies — the fulfillment-service launcher scans
`com.dyx.market.infrastructure`, which transitively requires `ITaskDao` and
`IUserRaffleOrderDao` mapper resolution at startup.

Validators still pass because the legacy-cleanup-readiness check accepts a
broad `compatibility set` or `mapper/mysql/*.xml` match in the inventory, but
the documentation is misleading about which copies are eligible for which
cleanup gate. Tightening this is a small documentation fix.

### Low

**L-1. Three near-duplicate `LocalActivityPortConfig` / `LocalStrategyDecisionConfig` configs.**

`account-service`, `message-job-service`, and `market-service` each have a
small `@Configuration` class that binds `IStrategyDecisionPort` and
`IAwardFulfillmentPort` to in-process beans via method references on
`IRaffleStrategy` and `IAwardService`. The three copies are byte-identical
except for package names.

This is intentional learning-mode wiring: each dark-launch launcher imports
its own local copy so that bean wiring is scoped to that module and does not
leak via component scan. Consolidating them into a shared module would be a
genuine refactor and is explicitly out of scope. This is recorded as a Low
finding rather than fixed.

**L-2. Phase-7-B mapper guard now relies on the service-module ownership validator to catch foreign additions.**

After `cfc3208` switched the guard to `--diff-filter=M`, brand-new mapper XMLs
added in service modules will not trigger the phase-7-B check. Coverage is
preserved by
`scripts/validate-microservices-service-module-ownership.sh`, which:

- forbids any mapper XML in `activity-service`;
- forbids non-strategy mapper XMLs in `strategy-service`;
- forbids `task_mapper.xml` references in `rebate-service`;
- whitelists exactly which `raffle_*`, `user_credit_*`, and
  `credit_award_task` mapper files may appear in `fulfillment-service` (only
  `user_credit_account_mapper.xml` and `credit_award_task_mapper.xml`).

The aggregate coverage is still meaningful. The trade-off is acceptable, but
the relationship deserves a one-line note in the phase-7-B validator so
future readers don't reintroduce the broader check by accident.

### Informational

- All required learning-mode tokens (`LOCAL-LEARNING-EVIDENCE`,
  `SIMULATED-CUTOVER-EVIDENCE`, `LEARNING-MODE-COMPLETE`) are present in every
  document that the learning-mode closure validator inspects.
- The runbook, completion index, roadmap, intake, and GO/NO-GO docs all
  explicitly state that production readiness is not proven by the local
  learning-mode closure.
- The archive index is a by-reference archive only; no historical phase doc
  was physically moved. Validators that reference historical docs continue to
  work.
- All production/remote/outbox/cutover flags remain default false in source
  config. Legacy provider flags (`REBATE_LEGACY_RPC_PROVIDER_ENABLED`,
  `STRATEGY_LEGACY_RPC_PROVIDER_ENABLED`) remain default true as expected.
- All local compatibility classes named in the legacy cleanup inventory exist
  on disk. None have been removed.

## Explicit Answers

- **Is the local learning-mode closure coherent?** Yes. The closure framing,
  validator suite, and runtime stack are mutually consistent. The only
  inconsistency is H-1 (tag vs. HEAD) and the small documentation gap in M-1.
- **Is real production readiness proven?** No. The closure documents repeatedly
  state that real DBA/Ops/Engineering/Oncall/Product evidence is not provided
  and that staging and production readiness are not proven. Validators confirm
  that no source-controlled production/remote/outbox/cutover flag is enabled
  by default.
- **Does the b15b458 tag vs. cfc3208-or-later HEAD situation require action?**
  Yes, a decision is needed. The current tag points to a commit whose aggregate
  validator fails. Two options exist:
  1. Re-tag `microservices-learning-mode-complete` to `cfc3208` so the tag
     and the green state agree. This needs explicit user authorization and a
     force-tag.
  2. Leave the tag at `b15b458` and document in the closure doc that
     `cfc3208` is the validator-clean tip and is required for green CI.
     This is the lowest-risk option and the one I recommend if the user is
     uneasy about moving the tag.
  See "Tag-vs-HEAD Decision" below for the recommended note.

## Remaining Limitations

- Real DBA, Ops, Engineering, Oncall, Product evidence remains EXTERNAL-GATED.
- Real staging and production cutover are not proven.
- 7-day stable and 30-day removal gates are not satisfied; no legacy provider
  or fallback was removed.
- The closure relies on simulated evidence; the LOCAL-LEARNING-EVIDENCE /
  SIMULATED-CUTOVER-EVIDENCE markers must remain in place.

## Tag-vs-HEAD Decision

The audit recommends leaving the tag at `b15b458` and adding a note to
`docs/microservices-learning-mode-closure.md` and
`docs/evidence/phase-8-local-learning-cutover-evidence.md` explaining that:

- the tag `microservices-learning-mode-complete` captures the closure intent
  at `b15b458`;
- the validator-clean tip is `cfc3208` (`docs: polish learning-mode closure`);
- consumers running the aggregate validator must check out `cfc3208` or
  later.

This avoids any tag rewrite, preserves the existing tag's audit trail, and
makes the validator-clean tip discoverable. If the user prefers re-tagging,
that is a separate, explicit follow-up.

## Proposed Phase 2 Fixes

The audit will apply the following small fixes, then re-run validators:

1. **M-1 fix.** Update
   `docs/microservices-legacy-cleanup-inventory.md` to explicitly classify
   `task_mapper.xml`, `user_raffle_order_mapper.xml` (mysql), and
   `user_raffle_order_mapper.xml` (elasticsearch) under `fulfillment-service`
   as local learning-mode compatibility copies, alongside the existing
   `user_credit_account_mapper.xml` and `credit_award_task_mapper.xml` entry.

2. **H-1 mitigation (documentation-only).** Add a short "Tag and validator-
   clean tip" subsection to
   `docs/microservices-learning-mode-closure.md` stating that the tag
   `microservices-learning-mode-complete` points to `b15b458` and that
   `cfc3208` is the validator-clean tip required for the aggregate gate to
   report green.

3. **L-2 clarification.** Add a one-line comment in
   `scripts/validate-microservices-phase-7-task-outbox-ownership.sh` near the
   `--diff-filter=M` mapper guard noting that new mapper additions are
   covered by `validate-microservices-service-module-ownership.sh`.

Each fix is scoped to the file named. No code paths, flag defaults, mappers,
adapters, fallback ports, or tag references will be removed.

After applying the fixes the audit will re-run:

- `scripts/validate-microservices-learning-mode-closure.sh`
- `scripts/validate-microservices-split-all-gates.sh`
- `scripts/validate-microservices-legacy-cleanup-readiness.sh`
- `scripts/validate-microservices-post-cutover-cleanup-gates.sh`

and append a "Fixes Applied" section to this report.

## Fixes Applied

All three proposed Phase 2 fixes were applied in the working tree (no commit
yet):

1. **M-1 fix.** Updated
   `docs/microservices-legacy-cleanup-inventory.md` so the fulfillment-service
   mapper exemption explicitly names the two service-owned files
   (`award_mapper.xml`, `user_award_record_mapper.xml`) and the five
   learning-mode compatibility copies
   (`user_credit_account_mapper.xml`, `credit_award_task_mapper.xml`,
   `task_mapper.xml`, `user_raffle_order_mapper.xml`,
   `mapper/elasticsearch/user_raffle_order_mapper.xml`). The classification
   reason — that `com.dyx.market.infrastructure` scanning transitively requires
   these DAOs — is recorded inline.

2. **H-1 mitigation (documentation-only).** Added a "Tag and Validator-Clean
   Tip" section to `docs/microservices-learning-mode-closure.md` describing
   the tag-vs-HEAD relationship and pointing consumers of the aggregate gate
   at `cfc3208` or later. The tag itself was not moved or recreated.

3. **L-2 clarification.** Added a one-line comment in
   `scripts/validate-microservices-phase-7-task-outbox-ownership.sh` next to
   the `--diff-filter=M` mapper guard, naming
   `validate-microservices-service-module-ownership.sh` as the validator that
   catches first-time mapper additions in dark-launch service modules.

### Regression Caught During Fixes

The first revision of the M-1 fix wrapped the words
`big-market-fulfillment-service` and `service-owned` onto separate lines.
That broke the legacy-cleanup-readiness validator's per-line grep
(`big-market-fulfillment-service.*service-owned`) and produced a 1-fail
state on the aggregate gate. The wording was tightened so both tokens appear
on the same line; the validator recovered to 80/80 PASS.

### Post-Fix Validator Re-Run

| Validator | Result |
|-----------|--------|
| `validate-microservices-learning-mode-closure.sh` | 61/61 PASS |
| `validate-microservices-split-all-gates.sh` | 25/25 gates PASS |
| `validate-microservices-master-plan.sh` | 48/48 PASS |
| `validate-microservices-production-flag-matrix.sh` | 21/21 PASS |
| `validate-microservices-legacy-cleanup-readiness.sh` | 80/80 PASS |
| `validate-microservices-post-cutover-cleanup-gates.sh` | 61/61 PASS |

### Working-Tree State

The working tree currently has the following uncommitted changes:

- modified: `docs/microservices-learning-mode-closure.md`
- modified: `docs/microservices-legacy-cleanup-inventory.md`
- modified: `scripts/validate-microservices-phase-7-task-outbox-ownership.sh`
- untracked: `docs/reviews/microservices-learning-mode-opus-review.md`

No commit was created automatically. A commit is recommended only if the user
explicitly authorizes one.

### Recommendations Not Acted On

- **Tag action.** The tag `microservices-learning-mode-complete` still points
  to `b15b458`. The audit recommends leaving it there and relying on the new
  "Tag and Validator-Clean Tip" note. If the user prefers to re-tag at
  `cfc3208` (or the eventual commit of these fixes), that requires an
  explicit force-tag and is out of scope for this audit.
- **L-1 (duplicate Local*Config classes).** No fix applied; consolidation
  would be a real refactor and is out of scope for closure polish.
- **Code-level cleanup of mapper copies, adapters, fallback ports.** Not
  performed; every item remains EXTERNAL-GATED.

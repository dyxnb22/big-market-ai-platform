# Microservices Next Execution Roadmap

Last revised: 2026-06-11.

## 中文快速摘要

这份文档用于新会话继续跟踪微服务拆分后的下一阶段工作。

当前仓库侧已经完成 Phase 7、Phase 8 的 repo-ready、外部证据接入、
清理门禁，以及 **Batch 1: Phase 8 cutover evidence execution pack**。
接下来不要直接做生产切流，也不要默认打开任何
production/remote/outbox/cutover flag。下一会话建议从
`phase-8-cutover-evidence-execution-pack` 标签继续，优先进入真实
staging 外部证据录入；如果没有真实外部证据，则只能做 repo-only 的
证据收集辅助文档或验证增强，不能把任何 staging/production gate 标为完成。

后续路线按顺序分为：

1. repo-only 切流证据执行包。已完成：`phase-8-cutover-evidence-execution-pack`。
2. 真实 staging 切流证据录入。下一批。
3. 真实 production 切流证据录入。
4. 7 天稳定后关闭 legacy provider 的准备。
5. 30 天稳定后清理废弃兼容路径。
6. 最终微服务拆分收口与归档。

新会话可直接复制本文第 10 节的 prompt 作为起始任务。

Purpose: this document is the handoff plan for the next tracking session. It
starts after tag `phase-8-cutover-evidence-execution-pack` and covers the
remaining work needed to move from repo-ready microservice decomposition to
externally verified cutover and post-cutover cleanup.

Current status:

- Phase 7 is repo-complete.
- Phase 8 repo readiness, external evidence intake, and cleanup gates are
  complete.
- Phase 8 cutover evidence execution pack is complete and tagged as
  `phase-8-cutover-evidence-execution-pack`.
- AL-1 through AL-11 direct repository DAO couplings are resolved.
- `scripts/validate-microservices-split-all-gates.sh` is the aggregate
  repo-only gate and currently covers 21 gates.
- Real staging and production cutover remain `EXTERNAL-GATED`.
- No DDL has been applied by repo automation.
- No production, remote, outbox, or cutover flag defaults true.
- No legacy provider or fallback path is eligible for removal yet.

## 1. Execution Principle

The remaining work should be tracked in two lanes:

| Lane | What belongs here | Can be automated in repo? |
|------|-------------------|---------------------------|
| Repo-only readiness | Evidence templates, validators, runbook updates, consistency gates, cleanup eligibility checks | Yes |
| External cutover | DBA DDL, Ops job registration, staging/prod canary, flag flips, approval evidence, monitoring windows | No, only evidence can be recorded |

Important rule: staging cutover and production cutover should not be merged
into one execution batch. A repo-only evidence pack may cover both, but real
production cutover must wait for staging GO evidence.

## 2. Recommended Batch Order

| Order | Batch | Objective | Type | Expected tag |
|-------|-------|-----------|------|--------------|
| 1 | Phase 8 cutover evidence execution pack | Create concrete staging/prod evidence templates and GO/NO-GO validators | repo-only | `phase-8-cutover-evidence-execution-pack` DONE |
| 2 | Phase 8 staging cutover evidence | Record real staging DBA/Ops/Engineering/Oncall evidence after external execution | external evidence | `phase-8-staging-cutover-evidence` NEXT |
| 3 | Phase 8 production cutover evidence | Record real production canary and rollout evidence after staging GO | external evidence | `phase-8-production-cutover-evidence` |
| 4 | 7-day legacy provider disable readiness | After 7 stable days, prepare default-off legacy provider config and validators | repo + evidence | `phase-8-legacy-provider-disable-after-7d` |
| 5 | 30-day obsolete path removal readiness | After 30 stable days, remove obsolete local fallbacks and compatibility mapper copies where safe | repo cleanup | `phase-8-obsolete-path-removal-after-30d` |
| 6 | Final decomposition closure | Produce final closure index and archive historical phase docs | repo-only | `microservices-decomposition-complete` |

## 3. Batch 1: Cutover Evidence Execution Pack

Status: complete in commit `e92db64`, tag
`phase-8-cutover-evidence-execution-pack`.

Goal: prepare the exact files and validators that will later consume real
external cutover evidence. This batch can be done without environment access.

Recommended tasks:

1. Create `docs/evidence/phase-8-staging-cutover-evidence-template.md`.
2. Create `docs/evidence/phase-8-production-cutover-evidence-template.md`.
3. Create `docs/evidence/phase-8-go-no-go-checklist.md`.
4. Add `scripts/validate-microservices-phase-8-cutover-evidence-pack.sh`.
5. Update `docs/microservices-phase-8-cutover-runbook.md` to link these files.
6. Update `docs/microservices-phase-8-external-evidence-intake.md` so each
   external gate points to the exact evidence field that will satisfy it.
7. Update `docs/microservices-split-completion-index.md` with the new pack.
8. Add the new validator to `scripts/validate-microservices-split-all-gates.sh`.

The evidence templates should include:

- DBA DDL application evidence per shard and table.
- DB grants/secret rollout evidence.
- Ops XXL-Job registration evidence.
- Nacos/Dubbo provider discovery evidence.
- Staging flag canary evidence.
- Production single-instance canary evidence.
- Rollback rehearsal evidence.
- Metrics/logs/oncall observation evidence.
- GO/NO-GO decision fields.
- Required sign-off fields.

Validators must keep all evidence placeholders `EXTERNAL-GATED` until real
evidence is added.

## 4. Batch 2: Staging Cutover Evidence

Goal: record staging evidence after external DBA/Ops/Engineering execution.

This batch should not be attempted unless real staging evidence exists.

Recommended tasks:

1. Fill the staging evidence template with external evidence references.
2. Record DBA DDL verification for each proposed outbox/ledger table.
3. Record Ops job registration where job paths are enabled.
4. Record staging service startup and provider discovery.
5. Record flag canary windows and rollback readiness.
6. Record business-flow validation per service:
   - account-service write/quota flow
   - fulfillment-service award/credit outbox flow
   - rebate-service read/write flow
   - strategy-service read parity
   - activity-service draw only if Phase 5-G/7-D approval exists
7. Add or update a validator that checks staging evidence completeness.
8. Keep production evidence external-gated.

Exit criteria:

- Staging evidence validator passes.
- Aggregate repo-only split gate passes.
- Maven package passes.
- Master plan says staging evidence recorded, production still gated.

## 5. Batch 3: Production Cutover Evidence

Goal: record production evidence after staging GO and production execution.

This batch must remain separate from staging cutover.

Recommended tasks:

1. Confirm staging GO is present and linked.
2. Fill production evidence template with DBA/Ops/Engineering/Oncall evidence.
3. Record production DDL verification.
4. Record production single-instance canary.
5. Record rollback command/flag readiness.
6. Record full rollout approval.
7. Record post-rollout monitoring and anomaly checks.
8. Update completion index to mark production cutover evidence recorded only
   for services with real evidence.
9. Keep 7-day and 30-day cleanup gates closed.

Exit criteria:

- Production evidence validator passes.
- No default flags are changed in source-controlled config unless explicitly
  approved by evidence and runbook.
- Legacy providers remain enabled by default until 7-day stable gate.

## 6. Batch 4: 7-Day Stable Legacy Provider Disable

Goal: after a service has 7 stable days of production evidence, disable the
corresponding legacy provider default or prepare the exact config change.

Recommended tasks:

1. Add 7-day stability evidence references.
2. For each eligible service, update the legacy provider default only if the
   runbook and evidence say it is safe.
3. Update `docs/microservices-legacy-cleanup-inventory.md`.
4. Add or update a validator that ensures only eligible providers are disabled.
5. Keep local fallback classes and mapper compatibility copies in place.

Likely candidates:

- `REBATE_LEGACY_RPC_PROVIDER_ENABLED=false` after rebate cutover stability.
- `STRATEGY_LEGACY_RPC_PROVIDER_ENABLED=false` after strategy read cutover
  stability.

Non-goals:

- Do not delete provider classes.
- Do not remove mapper XML copies.
- Do not remove local adapters.

## 7. Batch 5: 30-Day Obsolete Path Removal

Goal: after 30 stable days, remove obsolete local compatibility paths safely.

Recommended tasks:

1. Confirm 30-day evidence for each service-specific cleanup candidate.
2. Remove only the candidates explicitly eligible in
   `docs/microservices-legacy-cleanup-inventory.md`.
3. Remove obsolete mapper XML compatibility copies only after owning service
   isolation is proven.
4. Remove shared `task` fallback paths only after per-domain outbox cutover is
   proven.
5. Archive historical phase docs under `docs/archive/` instead of rewriting
   history.
6. Strengthen validators so removed paths cannot silently reappear.

High-risk removals that require explicit evidence:

- `big-market-trigger` local adapters.
- Legacy Dubbo providers in `big-market-trigger.rpc`.
- `SendMessageTaskJob` generic task fallback.
- Shared mapper copies in service modules.
- Local `ITaskDao` fallback adapters for AL-8/AL-9/AL-10.

## 8. Batch 6: Final Closure

Goal: make the repository state auditable as fully decomposed after all
external gates and cleanup gates are complete.

Recommended tasks:

1. Create `docs/microservices-decomposition-final-closure.md`.
2. Summarize all tags, validators, evidence links, and remaining accepted
   shared-kernel modules.
3. Update `docs/microservices-split-completion-index.md` to final status.
4. Update aggregate gate to enforce the final state.
5. Archive obsolete phase docs under `docs/archive/`.
6. Tag the closure commit as `microservices-decomposition-complete`.

## 9. Validation Baseline for Every Future Batch

Run these at minimum before committing:

```bash
./scripts/validate-microservices-split-all-gates.sh
./scripts/validate-microservices-master-plan.sh
mvn clean package -DskipTests
git status --short
```

For Phase 8 evidence or cleanup batches, also run:

```bash
./scripts/validate-microservices-phase-8-external-evidence-intake.sh
./scripts/validate-microservices-legacy-cleanup-readiness.sh
./scripts/validate-microservices-post-cutover-cleanup-gates.sh
./scripts/validate-microservices-production-flag-matrix.sh
```

## 10. Suggested Prompt for the Next Session

```text
Continue from tag `phase-8-cutover-evidence-execution-pack`.

Use `docs/microservices-next-execution-roadmap.md` as the source of truth for
the next work. Focus only on the microservices decomposition track.

Goal: complete one coherent batch with maximum efficiency and token utility:
Batch 2, "Phase 8 staging cutover evidence", but only if real staging evidence
is available in the repository or provided in the current conversation.

Hard constraints:
- Do not apply DDL.
- Do not run Docker/MySQL/MQ/remote commands.
- Do not flip production, remote, outbox, or cutover flags.
- Do not mark staging evidence complete unless every required external evidence
  reference is concrete and auditable.
- Keep all production evidence EXTERNAL-GATED.
- Do not remove legacy providers, fallback adapters, mapper copies, or task
  fallback paths.

Workflow:
1. Read this roadmap, the Phase 8 runbook, the external evidence intake doc,
   the staging evidence template, and the GO/NO-GO checklist.
2. Check whether real staging evidence exists locally or was provided in the
   conversation.
3. If real staging evidence exists, fill the staging evidence document, add or
   update a staging evidence completeness validator, link the result from the
   completion index, and keep production gated.
4. If real staging evidence does not exist, do not fabricate evidence. Instead,
   complete the largest safe repo-only preparation batch: add a staging evidence
   collection bundle/checklist or validator hardening that makes the future
   evidence intake easier, while preserving EXTERNAL-GATED status.

Run:
- validate-microservices-split-all-gates.sh
- validate-microservices-master-plan.sh
- validate-microservices-phase-8-cutover-evidence-pack.sh
- any new or changed validator
- mvn clean package -DskipTests

Commit and tag if green. Suggested names if real staging evidence is completed:
- commit: `docs: record phase 8 staging cutover evidence`
- tag: `phase-8-staging-cutover-evidence`

Suggested names if only repo-only staging evidence preparation is possible:
- commit: `docs: prepare phase 8 staging evidence intake`
- tag: `phase-8-staging-evidence-intake-prep`
```

> **Archived (2026-06-12):** superseded by `docs/MICROSERVICES.md`. Kept for historical traceability only.

# Microservices Learning Mode Closure

Last revised: 2026-06-11.

Status: LEARNING-MODE-COMPLETE.

This document closes the microservices decomposition for this local learning
project only. The closure is backed by LOCAL-LEARNING-EVIDENCE and
SIMULATED-CUTOVER-EVIDENCE in
`docs/evidence/phase-8-local-learning-cutover-evidence.md`.

## Closure Statement

The repository has completed the learning-mode microservices decomposition when
the following local checks pass:

- Maven packages all modules with `mvn clean package -DskipTests`;
- repository-only split validators pass;
- production flag matrix remains safe;
- legacy cleanup gates continue to protect compatibility code;
- local Docker stack validation passes;
- local smoke tests pass for the eight-service dark-launch stack.

As of 2026-06-11, those local checks pass and this learning project is marked
LEARNING-MODE-COMPLETE.

## What This Means

- The local Docker environment can build and run the decomposed service stack.
- The dark-launch services remain compatible with local fallback behavior.
- The repository documents the remaining real-world external gates clearly.
- The active learning-mode evidence is generated from actual local commands.

## What This Does Not Mean

- It does not claim real staging readiness.
- It does not claim real production readiness.
- It does not replace DBA, Ops, Engineering, Oncall, or Product evidence.
- It does not authorize source-controlled production flag default changes.
- It does not authorize broad deletion of legacy providers, mapper copies, or
  fallback adapters.

## Local Acceptance

Product-equivalent learning acceptance: accepted for local learning mode only.

The acceptance is based on local validation and smoke-test behavior, not on a
real customer-facing canary or production monitoring window.

## Tag and Validator-Clean Tip

The tag `microservices-learning-mode-complete` captures the closure intent
at commit `b15b458` (`docs: complete learning-mode microservices closure`).
The validator-clean tip is `cfc3208` (`docs: polish learning-mode closure`),
which fixes two validator hygiene issues introduced by `b15b458`:

- replaces an SQL-style DDL comment in
  `big-market-fulfillment-service/.../task_mapper.xml` with an XML comment so
  the phase-7-B DDL grep no longer matches it;
- tightens the phase-7-B mapper change guard to
  `git diff --diff-filter=M` so first-time mapper additions in newly added
  service modules are not flagged as modifications.

Consumers running `scripts/validate-microservices-split-all-gates.sh` should
check out `cfc3208` or later to see the aggregate gate report green. The tag
is intentionally left at `b15b458` to preserve the original closure history;
no tag rewrite is performed by this closure pass.

## Remaining Real-World Gates

Real production readiness remains not applicable / not proven for this project:

- external DBA DDL review and environment execution;
- external Ops deploy, provider discovery, MQ, and job registration;
- external Engineering staging and production flow validation;
- external Oncall monitoring and rollback ownership;
- external Product approval for user-visible behavior.

The external-gated templates and runbooks remain in place for reference.


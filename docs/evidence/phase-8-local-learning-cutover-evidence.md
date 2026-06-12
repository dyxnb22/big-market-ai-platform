# Phase 8 Local Learning Cutover Evidence

Last revised: 2026-06-11.

Status: LOCAL-LEARNING-EVIDENCE. This is SIMULATED-CUTOVER-EVIDENCE generated
from local Docker, local Maven, local scripts, and repository validators for a
learning project. It is not real DBA, Ops, Engineering, Oncall, or Product
evidence, and it does not prove staging or production readiness.

Result: LEARNING-MODE-COMPLETE for the local microservices decomposition path.

## Scope

This evidence closes the decomposition for the local learning environment only:

- local Docker and local infrastructure services;
- local Maven package output;
- repository-only validators;
- local smoke tests and service health checks;
- static proposed SQL validation.

Real staging or production execution remains not applicable / not proven for
this project, and the external production path remains EXTERNAL-GATED. No
source-controlled production, remote, outbox, or cutover flag default was
changed to true.

## Simulated Evidence Summary

| Evidence role | Local simulated substitute | Result |
|---------------|----------------------------|--------|
| DBA-equivalent | `./scripts/validate-production-ddl.sh` static SQL validation for proposed DDL | PASS: 14 pass, 0 fail; DB verification skipped because no remote DB verification mode was requested |
| Ops-equivalent | `docker compose ps` and `./scripts/validate-microservices-stack.sh --skip-build` | PASS: 8 local application services healthy after rebuild |
| Engineering-equivalent | `mvn clean package -DskipTests`, aggregate validators, smoke test | PASS: Maven reactor success; stack smoke 18/18 |
| Oncall-equivalent | local Docker health/status and logs during failed and passing runs | PASS after local wiring fixes; no real oncall window exists |
| Product-equivalent | learning-project acceptance statement in `docs/MICROSERVICES.md` §4.2 and `docs/archive/microservices-history/microservices-learning-mode-closure.md` | Accepted for local learning mode only |

## Commands Executed

The local evidence path used these commands:

```bash
git status --short
git tag --sort=-creatordate
./scripts/validate-microservices-phase-8-cutover-evidence-pack.sh
./scripts/validate-microservices-phase-8-staging-evidence-intake.sh
./scripts/validate-microservices-phase-8-staging-evidence-consistency.sh
./scripts/validate-microservices-production-flag-matrix.sh
./scripts/validate-production-ddl.sh
mvn clean package -DskipTests
docker compose ps
./scripts/validate-microservices-stack.sh --skip-build
./scripts/smoke-test-phase-1.sh
docker compose logs --tail=220 big-market-account-service
docker compose logs --tail=220 big-market-message-job-service
docker compose logs --tail=220 big-market-fulfillment-service
```

Final validation also includes:

```bash
./scripts/validate-microservices-learning-mode-closure.sh
./scripts/validate-microservices-split-all-gates.sh
./scripts/validate-microservices-master-plan.sh
./scripts/validate-microservices-legacy-cleanup-readiness.sh
./scripts/validate-microservices-post-cutover-cleanup-gates.sh
```

## Local Docker Evidence

The final stack validation rebuilt the service images from the current Maven
artifacts and passed:

```text
ALL CHECKS PASSED. Stack is healthy.
```

The final smoke output was:

```text
Results: 18 passed, 0 failed  (expected 18/18)
```

Services covered by the final local smoke path:

- auth-service
- admin-service
- market-service
- chatbot-service
- gateway
- message-job-service
- account-service dark-launch surface
- fulfillment-service dark-launch surface

Earlier local attempts exposed missing dark-launch compatibility wiring for
account-service, message-job-service, and fulfillment-service. Those were fixed
locally and revalidated before this evidence was marked LEARNING-MODE-COMPLETE.

## Local Build Evidence

`mvn clean package -DskipTests` completed successfully after the local service
wiring fixes.

```text
Reactor Summary for big-market 1.1: all 26 modules SUCCESS
BUILD SUCCESS
Finished at: 2026-06-11T21:03:20+08:00
```

## Local Architecture Evidence

The local closure preserved these safety constraints:

- remote/outbox/cutover/production flag defaults remain safe;
- compatibility adapters and mapper copies were retained where cleanup gates
  still require them;
- no real production evidence claim was introduced;
- local service startup fixes were limited to dark-launch compatibility wiring.

Searches performed during the closure pass covered TODO/FIXME markers, direct
DAO ownership references, flag defaults, and accidental production claims.

## Limitations

- Real DBA evidence: not provided and not applicable to this local learning run.
- Real Ops evidence: not provided and not applicable to this local learning run.
- Real Engineering staging or production evidence: not provided.
- Real Oncall evidence: not provided.
- Real Product approval: not provided; the acceptance is learning-mode only.
- Real staging and production readiness: not proven.

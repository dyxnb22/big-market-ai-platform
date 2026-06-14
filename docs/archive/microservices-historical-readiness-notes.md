# Archived Microservices Readiness Notes

Status: historical archive. This file is retained because existing references
and compatible scripts still point to it. It is not the current architecture
entry point. Use `docs/MICROSERVICES.md` for the final-state architecture.

## What Remains Useful

- DDL topics: quota ledger, award-credit task, rebate task outbox,
  credit-trade task outbox, award-dispatch task outbox.
- Service registration topics: Dubbo providers, Nacos registry, application
  health endpoints.
- Runtime topics: RabbitMQ, XXL-Job, Prometheus, Grafana, trace ids, logs.
- Acceptance topics: local build, smoke tests, guardrail scripts, and
  explainable rollback/idempotency behavior.

## Current Learning Replacement

The current learning documents are:

- `docs/MICROSERVICES.md`
- `docs/production-readiness-learning.md`
- `docs/operations-checklist.md`
- `docs/data-and-outbox.md`
- `docs/learning/README.md`

## Historical Notes

Earlier notes described external production evidence, canary steps, and timed
cleanup windows. Those are not part of the current portfolio completion
standard. The learning standard is local build success, local smoke validation,
runtime guardrail validation, and documentation/code consistency.

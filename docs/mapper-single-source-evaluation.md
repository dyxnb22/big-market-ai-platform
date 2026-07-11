# Mapper XML Single-Source Evaluation

Status: evaluation only (BM-017 / GOV-D03). No generator wired yet.

## Current state

- About **101** MyBatis mapper XML files under service `src/main/resources/mybatis/mapper/mysql/`.
- Roughly **~30 unique** mapper basenames, copied into launchers that need those DAOs (market, message-job, account, fulfillment, strategy, rebate, chatbot, …).
- Drift is partially gated by `scripts/validate-mapper-ddl-gates.sh` (shared statement-id / body checks). Physical single-source remains deferred.

## Options

| Option | Pros | Cons |
| --- | --- | --- |
| **A. Generate / copy-on-build** from one canonical tree (e.g. `big-market-infrastructure` or `docs`-adjacent source) into each service resources | One edit path; CI can fail on missing copy | Build complexity; IDE “source of truth” less obvious; need clear ownership of which services receive which files |
| **B. Shared Maven module** packaging mapper XML on the classpath | No per-service copies; single artifact | All consumers see all mappers unless filtered; risk of accidental statement-id collisions; contradicts “launcher-owned classpath” habit |
| **C. Keep copies + strengthen gates** (status quo+) | Lowest churn; gates already exist | Manual sync cost; 101 files remain |

## Recommendation

Prefer **Option A (generate/copy-on-build)** over a fat shared-module classpath for this repo: launchers already curate subsets, and statement-id uniqueness is sensitive.

**Next step (pilot):** pick **1–2** high-churn mappers (e.g. `task_mapper.xml`, `pending_remote_write_task_mapper.xml` or another pair touched by market + message-job), introduce a single canonical file plus a Maven resource-copy (or small script invoked from the parent build), keep existing DDL/statement gates, and expand only after the pilot is boring.

Do not migrate all 101 files in one PR.

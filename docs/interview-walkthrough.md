# Interview Walkthrough

Portfolio talking tracks for the Big Market AI Platform. Prefer **code + acceptance evidence** over archive docs (`docs/archive/` is historical only).

## 5 minutes — elevator

1. **What:** Learning/portfolio marketing raffle platform — Spring Boot microservices with a final 7-service runtime (gateway, auth, admin, market, chatbot, message-job, account); strategy and rebate stay inside market, while award credit dispatch stays in message-job.
2. **Why interesting:** Shared domain/infrastructure kernels with independent launchers; money-like paths use idempotency keys, outbox/`task` rows, and reconcile jobs.
3. **Honest readiness:** `main` passes clean Maven, static gates, and full reuse acceptance (including raffle award, chat refund, and two Playwright runs); fresh and secure runtime modes remain unverified.
4. **Frontend:** Static `big-market-web` (HTML/JS) via gateway `:8080`, not React.
5. **Pointer:** Current baseline `docs/LEARNING-FREEZE.md`; architecture `docs/MICROSERVICES.md`; historical pre-acceptance evidence `docs/audit/2026-07-17-learning-freeze-audit.md`.

## 15 minutes — architecture & flows

1. Draw the final service map (8080–8086), then point out the local market boundaries for strategy/rebate and the message-job award outbox.
2. Happy path: login (JWT) → stage activity → draw → award message → local award/outbox → account credit.
3. Async: RabbitMQ consumers + XXL-Job in **message-job** only (market must not scan `trigger.job` / `trigger.listener`).
4. Consistency: outbox, pending remote write, stock confirm, chat refund reconcile; DLQ review before replay.
5. Boundaries: DAO ownership doc + ArchUnit (`DomainArchitectureTest`, `MarketServiceArchitectureTest`, `MessageJobArchitectureTest`); mapper copy drift gates.
6. Ops: Prometheus business gauges; secure profile for non-default credentials.

## 30 minutes — deep dive & tradeoffs

1. Walk one money path end-to-end (e.g. SKU credit exchange or award credit) with idempotency and UNKNOWN/timeout handling (`docs/data-and-outbox.md`, money-path skill constraints).
2. Explain why health checks were insufficient and how database/business-terminal evidence exposed a false-green award path.
3. Show a Context/`@SpringBootTest` or smoke script; call out false-green risks of static-only validators.
4. Discuss tech debt consciously: the historical Java 8 / Boot 2.7 baseline, mapper XML copies (~101), frontend modularization plan, and the completed Java 17/Boot 3 migration.
5. Q&A: what you would **not** do next (premature physical DB split, claiming production HA).

## Suggested demos (if environment is up)

```bash
./scripts/web-start.sh          # UI :5173
./scripts/smoke-api.sh          # API via gateway
./scripts/acceptance.sh --reuse # when stack already healthy
```

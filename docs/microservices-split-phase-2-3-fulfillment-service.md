# Phase 2.3: big-market-fulfillment-service Extraction

## 1. What moves

**Domain code:** `com.dyx.market.domain.award`
- `AwardService` — orchestrates award dispatch (credits, OpenAI quota)
- `UserCreditRandomAward` — awards random credit amounts
- `OpenAIAccountAdjustQuotaAward` — adjusts OpenAI quota for a user
- `IAwardService` — domain service interface

**Tables eventually owned by fulfillment-service:**
- `user_award_record` (sharded: `user_award_record_000..003`)
- `award`

**MQ trigger:** `send_award` RabbitMQ topic — currently consumed by `SendAwardConsumer` in message-job-service. In a future batch, this consumer will call `FulfillmentAwardServiceRPC` via Dubbo instead of calling `IAwardService` in-process.

## 2. The outbox dependency — why Phase 2.3-B must come before traffic cutover

`UserCreditRandomAward.distributeAward` calls `AwardRepository.saveGiveOutPrizesAggregate`, which (when `account.award-credit-outbox.enabled=false`, the current default) writes **both** `user_credit_account` and `user_award_record` inside a single local `transactionTemplate.execute()` block.

This is an intentional design (documented in Phase 2.2-B4 audit): the two writes share a local transaction to prevent partial-credit scenarios. Splitting them across service boundaries requires a distributed transaction strategy.

The credit-award outbox (`credit_award_task` tables + `DispatchCreditAwardTaskJob`) was built in Phase 2.2-B5/B6 precisely to break this boundary safely:
1. `AwardRepository` inserts an outbox row (atomically with `user_award_record`) when `award-credit-outbox.enabled=true`
2. `DispatchCreditAwardTaskJob` polls the outbox and dispatches credit to account-service via `IAccountCreditWriteAdapter.createOrder()`
3. Account-service deduplicates using `outBusinessNo=awardOrderId`

**Consequence:** The fulfillment-service MUST NOT receive live `send_award` traffic until:
- `credit_award_task` DDL is applied to staging (see `docs/sql/proposed-credit-award-task-outbox.sql`)
- `award-credit-outbox.enabled=true` is validated in staging
- The outbox poller runs successfully in message-job-service (or fulfillment-service)
- Evidence is filed and the staging GO decision is made

Enabling fulfillment-service traffic cutover while `award-credit-outbox.enabled=false` would mean `user_credit_account` writes happen in fulfillment-service's DB shard instead of account-service's — breaking the isolation boundary.

## 3. Dubbo interface plan

**Current (Phase 2.3-A — dark launch):**
- `FulfillmentAwardServiceRPC implements IAwardService` registers as Dubbo provider (version 1.0) on port 20882
- No consumer wired to it; `SendAwardConsumer` in message-job-service still calls `IAwardService` in-process

**Phase 2.3-B (planned — after outbox staging validation):**
- Wire `SendAwardConsumer` to call `FulfillmentAwardServiceRPC` via `@DubboReference` instead of in-process
- Requires `account.award-credit-outbox.enabled=true` in fulfillment-service config
- Flag flip in message-job-service: keep `ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED=false` (outbox owned by fulfillment-service now)
- Validate end-to-end: raffle win → MQ → SendAwardConsumer → fulfillment-service RPC → outbox → DispatchCreditAwardTaskJob → account-service credit

**Phase 2.3-C (planned — production cutover):**
- Production DDL for outbox tables applied
- Traffic cutover flag enabled after staging evidence complete
- `user_award_record` and `award` tables logically owned by fulfillment-service

## 4. Remaining batches before production cutover

| Batch | Description | Blocked on |
|-------|-------------|-----------|
| **B23-B** | Wire SendAwardConsumer → FulfillmentAwardServiceRPC via Dubbo; enable outbox in fulfillment-service | Phase 2.2 staging GO (B17 evidence) |
| **B23-C** | Staging validation: outbox tables DDL applied, E2E award flow through fulfillment-service, evidence | B23-B + staging DB access |
| **B23-D** | Production promotion gate: static checks + evidence template + post-window checklist | B23-C evidence GO |
| **B23-E** | Production cutover: flag flip, traffic redirect, post-cutover verification | B23-D sign-off |

## 5. Known risk: UserCreditRandomAward writes user_credit_account directly

`UserCreditRandomAward` never calls `ICreditAdjustService`. The credit write goes through `AwardRepository.saveGiveOutPrizesAggregate` → `updateOrCreateCreditAccount` → `userCreditAccountDao` directly. This is NOT mediated by account-service's credit domain service.

The outbox (Phase 2.2-B6) was built to handle exactly this: when enabled, `saveGiveOutPrizesAggregate` inserts an outbox row, and `DispatchCreditAwardTaskJob` calls `IAccountCreditWriteAdapter.createOrder()` which routes to account-service's `ICreditAdjustService.createOrder()`.

**Action required before B23-B:** Confirm that `DispatchCreditAwardTaskJob` will run in fulfillment-service (not message-job-service) after the cutover, or add it to fulfillment-service's scan and remove from message-job-service. The job should live in whichever service owns the outbox tables. This decision should be made in B23-B design.

## 6. Phase 2.3-A dark launch summary

**Completed in this batch:**
- `big-market-fulfillment-service` module created (port 8087, Dubbo port 20882)
- `FulfillmentAwardServiceRPC` Dubbo provider registered; delegates to existing `AwardService` bean
- `account.award-credit-outbox.enabled=false` in all configs (gate confirmed by `validate-fulfillment-service-b23-a.sh` S4/S5/S6/S15)
- `docker-compose.yml` entry added; `ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED` env var wired
- Smoke test extended to 18/18
- `validate-fulfillment-service-b23-a.sh` 15/15 PASS
- All existing baseline scripts remain green (B18 12/12, B20 11/11, B17 6/6, B6 17/17, MQ 12/12, DDL 14/14)
- `mvn clean package -DskipTests`: BUILD SUCCESS (all 14 modules)

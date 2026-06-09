# Phase 2.2 — account-service Extraction Readiness Document

**Status: Phase 2.2-B5 award credit outbox strategy designed and scaffolded. Proposed `credit_award_task` outbox table DDL added under `docs/sql/` (proposed only — not wired to production code). `scripts/validate-award-credit-outbox-readiness.sh` added (8 static checks). Runtime behaviour unchanged. Phase 2.2-B4 award credit path audit complete. UserCreditRandomAward call chain documented; remote adapter wiring intentionally deferred due to transaction-boundary risk. Write flags still default false — full production cutover pending MQ idempotency verification and remaining path audits.**

## Phase 2.2-A — What Is Done

The following work was completed in the Phase 2.2-A dark launch batch:

- `big-market-account-service` Maven module created (port 8086)
- `IAccountCreditService` and `IAccountQuotaService` Dubbo API contracts added to `big-market-api`
- `CreditTradeRequestDTO` added to `big-market-api`
- `AccountCreditServiceRPC` and `AccountQuotaServiceRPC` implemented; delegate to existing domain services unchanged
- Service added to `docker-compose.yml` and smoke test (17/17 health check)
- `scripts/validate-microservices-stack.sh` argument parsing bug fixed

## Phase 2.2-B1 — Read-Only Remote Adapter

The following work was completed in Phase 2.2-B1:

**account-service provider hardening:**
- `AccountCreditServiceRPC.createOrder`: null-request guard added before log statement; invalid `tradeName` now returns `ILLEGAL_PARAMETER` instead of leaking `IllegalArgumentException`
- `resolveTradeNameVO` helper added to convert bad enum names to `AppException`

**Feature flag added to market-service:**
- `account.service.remote-read.enabled=false` (env: `ACCOUNT_SERVICE_REMOTE_READ_ENABLED`) in `application.yml`
- Default is `false` — all reads still use local domain services; no behaviour change

**Dubbo read clients and read-only adapter:**
- `IAccountReadAdapter` interface added in `big-market-trigger` (four read-only methods)
- `AccountRemoteReadAdapter` implements the interface in `big-market-market-service`
  - Has `@DubboReference(version="1.0", check=false)` for `IAccountCreditService` and `IAccountQuotaService`
  - When flag is `true` and remote call fails, falls back to local domain service and logs the error
- `LocalAccountReadAdapter` added in `big-market-trigger` as `@ConditionalOnMissingBean` fallback — used by `big-market-app` (monolith) which has no Dubbo clients

**Controllers wired via adapter (read-only only):**
- `RaffleActivityController`: `queryUserCreditAccount` and `queryUserActivityAccount` now route through `IAccountReadAdapter`
- `RaffleStrategyController`: `queryRaffleAwardList` (dayPartakeCount) and `queryRaffleStrategyRuleWeight` (totalPartakeCount) now route through `IAccountReadAdapter`
- All write paths (`draw`, `creditPayExchangeSku`, `calendarSignRebate`, etc.) are unchanged

## Phase 2.2-B — Remote-Read Validation + Write Scaffold

**Remote-read validation:**
- `docker-compose.yml` wires `ACCOUNT_SERVICE_REMOTE_READ_ENABLED=${ACCOUNT_SERVICE_REMOTE_READ_ENABLED:-false}` into market-service
- `scripts/validate-account-remote-read.sh` validates remote-read by recreating only `big-market-market-service` with `ACCOUNT_SERVICE_REMOTE_READ_ENABLED=true`
- The script exercises:
  - `query_user_credit_account`
  - `query_user_activity_account`
  - `query_raffle_award_list`
  - `query_raffle_strategy_rule_weight`
- It checks `code=0000`, prints `AccountRemoteReadAdapter` remote-success logs, stops account-service for one fallback read, restarts account-service, and restores market-service to `ACCOUNT_SERVICE_REMOTE_READ_ENABLED=false`

**Write scaffold prepared but disabled:**
- `account.service.remote-credit-write.enabled=false`
- `account.service.remote-quota-write.enabled=false`
- Env wiring defaults false:
  - `ACCOUNT_SERVICE_REMOTE_CREDIT_WRITE_ENABLED=false`
  - `ACCOUNT_SERVICE_REMOTE_QUOTA_WRITE_ENABLED=false`
- `IAccountCreditWriteAdapter` / `IAccountQuotaWriteAdapter` added in `big-market-trigger`
- Local write adapters keep `big-market-app` and default services on local domain services
- `AccountRemoteCreditWriteAdapter` / `AccountRemoteQuotaWriteAdapter` added in market-service launcher config with `@DubboReference(check=false)`
- `IAccountQuotaService` now includes write-scaffold RPCs for `createOrder` and `updateOrder`; account-service implements them
- No controllers, consumers, or domain services are routed through these write adapters in this batch

## Phase 2.2-B2 — MQ Write Consumer Adapter Wiring

**Completed (2026-06-09):**
- `CreditAdjustSuccessConsumer`: removed direct `IRaffleActivityAccountQuotaService` injection; now injects `IAccountQuotaWriteAdapter` and calls `updateOrder` through it
- `RebateMessageConsumer`: removed direct `IRaffleActivityAccountQuotaService` and `ICreditAdjustService` injections; now injects `IAccountQuotaWriteAdapter` (sku rebate) and `IAccountCreditWriteAdapter` (integral rebate)
- `AccountRemoteCreditWriteAdapter` and `AccountRemoteQuotaWriteAdapter` created under `big-market-message-job-service/config/` — active only when `account.service.remote-credit-write.enabled=true` / `remote-quota-write.enabled=true` (`@ConditionalOnProperty`)
- Local adapters (`LocalAccountCreditWriteAdapter`, `LocalAccountQuotaWriteAdapter`) registered via `WriteAdapterLocalConfig` (`@Bean @ConditionalOnMissingBean`) in message-job-service config — NOT via `com.dyx.market.trigger.adapter` package scan (avoided due to `@Component`/`@ConditionalOnMissingBean` evaluation-order issues in component scan)
- `@EnableDubbo` added to `MessageJobServiceApplication`; Dubbo configured with registry `check=false` so startup does not fail if nacos is unavailable when write flags are false
- `DUBBO_REGISTRY_ADDRESS=nacos://nacos:8848` wired into message-job-service docker-compose entry
- `scripts/validate-account-remote-write-scaffold.sh` added — confirms default flags false, health UP, and optionally recreates with flags=true to verify remote adapters load; does NOT publish real MQ messages

**Write flags remain false by default:**
- `ACCOUNT_SERVICE_REMOTE_CREDIT_WRITE_ENABLED=false` (env default)
- `ACCOUNT_SERVICE_REMOTE_QUOTA_WRITE_ENABLED=false` (env default)
- No transactional write traffic flows to account-service

**What is still NOT done (full production cutover):**
- MQ idempotency end-to-end verification (duplicate message replay safety per `outBusinessNo`)
- Business-flow validation: sign-in rebate, credit exchange, and raffle-win credit award with remote-write flags enabled in staging
- `UserCreditRandomAward` (credit award path) — call chain audited in Phase 2.2-B4; remote adapter wiring deferred until saga/outbox strategy exists
- `RaffleActivityPartakeService` quota decrement — deferred (high risk, needs dedicated decrement RPC)
- No domain packages removed from market-service scan
- No database schema changes
- No domain code relocated

**Gate check before enabling write flags in production:**
1. Docker 17/17 PASS: `./scripts/smoke-test-phase-1.sh` with full Docker stack
2. Remote-read validation: `./scripts/validate-account-remote-read.sh`
3. Write scaffold validation: `./scripts/validate-account-remote-write-scaffold.sh`
4. Manual business-flow verification with `ACCOUNT_SERVICE_REMOTE_QUOTA_WRITE_ENABLED=true` and `ACCOUNT_SERVICE_REMOTE_CREDIT_WRITE_ENABLED=true`

---

## Phase 2.2-B3 — HTTP Credit Exchange Write Adapter Wiring

**Completed (2026-06-09):**
- `RaffleActivityController.creditPayExchangeSku` routes both write steps through adapters:
  - Step 1 (quota order): was `raffleActivityAccountQuotaService.createOrder` → now `accountQuotaWriteAdapter.createOrder`
  - Step 2 (credit debit): was `creditAdjustService.createOrder` → now `accountCreditWriteAdapter.createOrder`
- Unused `IRaffleActivityAccountQuotaService` and `ICreditAdjustService` fields removed from the controller
- outBusinessNo flow preserved: quota adapter returns `UnpaidActivityOrderEntity.outBusinessNo`; credit adapter reuses that same `outBusinessNo` for idempotency
- `IAccountQuotaWriteAdapter` and `IAccountCreditWriteAdapter` injected; local adapters are active by default (flags false)
- No new adapter implementations needed — `AccountRemoteQuotaWriteAdapter` and `AccountRemoteCreditWriteAdapter` in market-service config already implement both interfaces from Phase 2.2-B validation
- Adapter interface Javadoc updated to reflect wired callers and pending callers

**Behavior when flags are false (default):**
- `LocalAccountQuotaWriteAdapter` delegates to `IRaffleActivityAccountQuotaService.createOrder` — identical to pre-B3 behavior
- `LocalAccountCreditWriteAdapter` delegates to `ICreditAdjustService.createOrder` — identical to pre-B3 behavior

**Remaining write callers still NOT wired:**
- `UserCreditRandomAward` — issues credit on award dispatch; needs full call-chain audit (domain layer, not just trigger layer)
- `RaffleActivityPartakeService` quota decrement — deferred; high-risk synchronous path; needs purpose-built decrement RPC before any cutover attempt

---

## Phase 2.2-B4 — Award Credit Path Audit

**Completed (2026-06-09).**

### Real call chain

```
SendAwardConsumer.listener()                         [big-market-trigger/listener/]
  └─ IAwardService.distributeAward(distributeAwardEntity)
       └─ AwardService.distributeAward()             [big-market-domain/award/service/]
            resolves awardKey → IDistributeAward bean (bean name: "user_credit_random")
            └─ UserCreditRandomAward.giveOutPrizes() [big-market-domain/award/service/distribute/impl/]
                 builds GiveOutPrizesAggregate {
                   UserAwardRecordEntity  (awardId, orderId, state=complete)
                   UserCreditAwardEntity  (userId, creditAmount — random in configured range)
                 }
                 └─ IAwardRepository.saveGiveOutPrizesAggregate(aggregate)
                      └─ AwardRepository.saveGiveOutPrizesAggregate()  [big-market-infrastructure/]
                           acquires Redis lock (ACTIVITY_ACCOUNT_LOCK + userId)
                           dbRouter.doRouter(userId)
                           transactionTemplate.execute() {
                             IUserCreditAccountDao.query/insert/updateAddAmount  → user_credit_account
                             IUserAwardRecordDao.updateAwardRecordCompletedState → user_award_record
                           }
```

**Key finding:** `UserCreditRandomAward` does NOT call `ICreditAdjustService`. The credit write is a
direct `IUserCreditAccountDao` call inside `AwardRepository`, in the same transaction as the
award-record update. This is a cross-domain DB write hidden inside the award domain's repository.

### Why remote adapter wiring is not safe yet

`AwardRepository.saveGiveOutPrizesAggregate` updates two tables atomically:

| Write | Table | Owner domain |
|-------|-------|-------------|
| credit account upsert | `user_credit_account` | credit / account-service |
| award record completion | `user_award_record` | award / fulfillment-service |

If the credit write is moved to a remote Dubbo call:

- **Partial success — credit granted, award not marked complete:** on MQ retry, the
  `updateAwardRecordCompletedState` idempotency guard returns 0 and rolls back, but the remote
  credit write already succeeded → credit issued twice.
- **Partial success — award marked complete, credit write fails:** the award record is permanently
  in `complete` state; the MQ consumer swallows `INDEX_DUP` on the next delivery → credit silently
  lost, no retry possible.

Neither failure mode is acceptable. The transaction boundary must either remain local or be replaced
with a saga / transactional outbox before any remote wiring can proceed.

### Code change in this batch

`AwardRepository.saveGiveOutPrizesAggregate` refactored for clarity only (no behaviour change):
- `buildCreditAccountReq()` private method extracts the PO construction
- `updateOrCreateCreditAccount()` private method names the direct credit-account write explicitly
- Comment added on the transaction block explaining the pending design constraint

### What a safe future approach looks like

Option A — **Outbox within award transaction:**
- Add a `credit_award_task` outbox row inside the same transaction as `user_award_record` update.
- A separate poller/consumer reads `credit_award_task` and calls `IAccountCreditWriteAdapter`.
- Idempotency: `credit_award_task.award_order_id` unique constraint prevents double-credit.

Option B — **Saga with compensating action:**
- Step 1: mark `user_award_record = processing` (local).
- Step 2: call account-service to issue credit; on failure, compensate by resetting award record.
- Requires a purpose-built `IAccountCreditService.issueAward(orderId, userId, amount)` RPC
  that is idempotent on `orderId`.

Neither option is implemented in this batch. Code behaviour is unchanged.

### Validation

Run `./scripts/validate-award-credit-path.sh` to statically verify:
1. `AwardRepository.java` still contains `userCreditAccountDao` (direct write, not removed).
2. `UserCreditRandomAward.java` does NOT import or reference `ICreditAdjustService`.
3. Both `userCreditAccountDao` and `userAwardRecordDao` writes are inside `saveGiveOutPrizesAggregate`.

---

## Phase 2.2-B5 — Award Credit Outbox Strategy (Design and Scaffold Only)

**Completed (2026-06-09). Runtime behaviour unchanged.**

This batch designs the transactional-outbox migration path for the award credit write. No production code is wired to the outbox table. The goal is to make the design concrete and machine-verifiable before any implementation batch begins.

### Current transaction boundary (unchanged)

```
AwardRepository.saveGiveOutPrizesAggregate()
  Redis lock (ACTIVITY_ACCOUNT_LOCK + userId)
  dbRouter.doRouter(userId)
  transactionTemplate.execute() {
    updateOrCreateCreditAccount()         -- writes user_credit_account
    updateAwardRecordCompletedState()     -- writes user_award_record
  }
```

Both writes share one local `transactionTemplate` block. Either both succeed or both roll back. This is safe but prevents remote credit write.

### Why direct `IAccountCreditWriteAdapter` wiring is still unsafe

Replacing the in-transaction `updateOrCreateCreditAccount` call with a remote Dubbo call creates two unacceptable failure modes:

| Failure scenario | Outcome |
|-----------------|---------|
| Network error after adapter call succeeds but before award-record commit | Award record never marked complete; MQ re-delivers; adapter called again → **double credit** |
| Award record committed; adapter call fails (timeout, provider down) | Award record is permanently `complete`; next MQ delivery hits `INDEX_DUP` guard → **credit silently lost**, no retry |

A transactional outbox eliminates both modes by keeping the dispatch intent inside the same transaction as the award-record update.

### Proposed outbox strategy: dedicated `credit_award_task` table

**Why not reuse the existing `task` table:**
The existing `task` table is an MQ-dispatch outbox — its poller (`SendMessageTaskJob`) publishes to a RabbitMQ topic and expects an MQ consumer on the other end. Award credit dispatch needs to call `IAccountCreditWriteAdapter.createOrder()` directly (a Dubbo RPC), not publish to MQ. Mixing dispatch semantics in one table complicates both the poller and the retry model. A dedicated table with a dedicated poller is cleaner and independently tune-able.

**Proposed outbox row fields:**

| Field | Type | Purpose |
|-------|------|---------|
| `id` | BIGINT AUTO_INCREMENT | Row id |
| `user_id` | VARCHAR(32) | Shard key — same as `user_award_record.user_id` |
| `activity_id` | BIGINT | Audit trail |
| `strategy_id` | BIGINT | Audit trail |
| `award_order_id` | VARCHAR(64) | **Idempotency key** — `UserAwardRecordEntity.getOrderId()`; UNIQUE per `(user_id, award_order_id)` |
| `credit_amount` | DECIMAL(10,2) | Amount to credit; captured at award time |
| `state` | VARCHAR(16) | `pending` → `dispatched` (success) or `failed` (max retries exceeded) |
| `retry_count` | TINYINT | Incremented on each failed dispatch attempt |
| `create_time` | DATETIME | Row creation |
| `update_time` | DATETIME | Last update (auto-updated) |

Full DDL: `docs/sql/proposed-credit-award-task-outbox.sql` (proposed only — do not run in production).

### Idempotency key

`award_order_id` = `UserAwardRecordEntity.getOrderId()`. This value is:
- Generated once per award dispatch in `UserCreditRandomAward.giveOutPrizes()`
- Already used as the unique key on `user_award_record`
- Stable across MQ retries (same message body, same orderId)

The `UNIQUE KEY uq_award_order_id (user_id, award_order_id)` on `credit_award_task` ensures at-most-one outbox row per award event. A duplicate INSERT inside the transaction is caught by `DuplicateKeyException`, which the caller already handles by rolling back and swallowing — identical to the existing `user_award_record` duplicate guard.

`IAccountCreditWriteAdapter.createOrder()` must itself be idempotent on `outBusinessNo` (= `award_order_id`). `CreditAdjustService.createOrder()` already enforces this via `UNIQUE KEY` on `user_credit_order.out_business_no`.

### Retry model

A new XXL-Job handler (or a `@Scheduled` fallback) scans `credit_award_task` for rows with `state = 'pending'` and `retry_count < MAX_RETRY` (suggested: 5). For each row:
1. Call `IAccountCreditWriteAdapter.createOrder(userId, creditAmount, awardOrderId)`.
2. On success: `UPDATE state = 'dispatched'`.
3. On `DuplicateKeyException` from the adapter (credit already issued): treat as success → `UPDATE state = 'dispatched'`.
4. On any other failure: `UPDATE retry_count = retry_count + 1`; if `retry_count >= MAX_RETRY`, `UPDATE state = 'failed'` and raise an alert.

The poller uses the same `dbRouter.setDBKey` / `dbRouter.setTBKey` pattern as `SendMessageTaskJob` to iterate over all shards.

### Rollback and compensation

- **Transaction rollback before commit:** `credit_award_task` row is never inserted → no orphaned outbox row; credit never issued; MQ re-delivers; clean retry.
- **Adapter failure after outbox row committed:** outbox row stays `pending`; poller retries; `DuplicateKeyException` on re-delivery is handled as success; credit eventually issued exactly once.
- **Adapter success but state update fails:** row stays `pending`; poller retries; adapter's own `DuplicateKeyException` guard prevents double credit; state update is retried and succeeds.

No compensating transaction (credit reversal) is needed in any of these paths because the outbox row either exists (credit will be issued) or doesn't (credit was never authorized).

### Required changes to `AwardRepository.saveGiveOutPrizesAggregate` (future batch)

```
transactionTemplate.execute() {
  // Remove: updateOrCreateCreditAccount(userCreditAccountReq)  ← moves to poller
  // Add:    creditAwardTaskDao.insert(creditAwardTaskRow)       ← new outbox row
  updateAwardRecordCompletedState(userAwardRecordReq)
}
// No post-transaction publish needed — poller drives delivery
```

This change must NOT be made until:
1. `credit_award_task` table is deployed and the poller job is running.
2. The poller has been validated in staging against replay scenarios.
3. `IAccountCreditWriteAdapter.createOrder` idempotency is confirmed end-to-end.

### Validation

Run `./scripts/validate-award-credit-outbox-readiness.sh` to statically verify:
1. `AwardRepository` does NOT reference `IAccountCreditWriteAdapter` (wiring still deferred).
2. Both credit-account and award-record writes remain inside `transactionTemplate.execute()` in `saveGiveOutPrizesAggregate`.
3. `docs/microservices-split-phase-2-2-account-service.md` contains the B5 outbox strategy section.
4. The doc explicitly forbids direct remote adapter wiring before outbox/saga.
5. `docs/sql/proposed-credit-award-task-outbox.sql` exists with `UNIQUE` constraint on `award_order_id`.
6. No production Java source references `credit_award_task` table (wiring deferred).

---

**How to validate remote reads:**
```bash
./scripts/validate-account-remote-read.sh
```

Manual fallback if the script reports data assumptions:
```bash
# Start Docker stack
docker compose up -d

# Temporarily enable remote reads in market-service container
# (or set env var and restart)
export ACCOUNT_SERVICE_REMOTE_READ_ENABLED=true

# Exercise read endpoints
curl -X POST http://localhost:8083/api/v1/raffle/activity/query_user_credit_account \
  -d "userId=xiaofuge" -H "Content-Type: application/x-www-form-urlencoded"

curl -X POST http://localhost:8083/api/v1/raffle/activity/query_user_activity_account \
  -H 'content-type: application/json' \
  --data '{"userId":"xiaofuge","activityId":100301}'

# Confirm logs show "[AccountRemoteReadAdapter]" remote success lines when flag is true
# Confirm results match when flag is false (local) vs true (remote)
```

---

**Gate check for Phase 2.2-B2:** `./scripts/smoke-test-phase-1.sh` must return **17/17 PASS** and `./scripts/validate-account-remote-read.sh` must pass before starting write cutover.

---

## 1. Proposed Service

| Field | Value |
|-------|-------|
| Service name | `big-market-account-service` |
| Maven module | `big-market-account-service/` |
| Port | **8086** |
| Spring Boot launcher | `AccountServiceApplication` |
| docker-compose entry | `big-market-account-service: 8086:8086` |

---

## 2. Domain Packages Involved

These packages live in the shared `big-market-domain` JAR today. After extraction they
remain in the shared JAR (no code is moved); account-service restricts its scan to include
them and market-service will no longer scan them directly.

| Package | What it contains |
|---------|-----------------|
| `com.dyx.market.domain.credit` | `CreditAdjustService`, `ICreditAdjustService`, credit domain model, credit repository interface |
| `com.dyx.market.domain.activity.service.quota` | `ActivityAccountQuotaService`, `IActivityAccountQuotaService` — manages per-user activity quota accounts (total / day / month) |

**Note:** `com.dyx.market.domain.activity` is a large package. Only the quota sub-package
moves to account-service ownership. The raffle participation service
(`IRaffleActivityPartakeService`) stays in market-service.

---

## 3. Tables Likely Owned by account-service

These tables are written by the domain packages listed above. Once account-service is
extracted, no other service should write to them directly.

| Table | Owner domain |
|-------|-------------|
| `user_credit_account` | credit domain — user credit balance |
| `user_credit_order` | credit domain — credit earn/spend ledger |
| `raffle_activity_account` | quota domain — per-user total quota account |
| `raffle_activity_account_day` | quota domain — per-user daily quota |
| `raffle_activity_account_month` | quota domain — per-user monthly quota |

**Phase 3 consideration:** These tables still reside in the shared MySQL instance in Phase
2.2. Actual schema isolation (separate schema/user grants) is a Phase 3 concern. In Phase
2.2 we only change which JVM writes to them.

---

## 4. Current Callers / Dependencies to Audit Before Extraction

Before extraction, map every callsite that touches account-service domain logic.

### 4.1 Callers of `ICreditAdjustService`

| Caller | Location | Notes |
|--------|----------|-------|
| `CreditAdjustSuccessConsumer` | `big-market-trigger/listener/` | Runs in message-job-service. Will need to call account-service via Dubbo after extraction. |
| `SignInRebateStrategy` (rebate) | `big-market-domain/rebate/` | Calls `ICreditAdjustService` via `BehaviorRebateService`. Must go through Dubbo after extraction. |

**Correction (Phase 2.2-B4 audit):** `UserCreditRandomAward` does NOT call `ICreditAdjustService`. It builds a `GiveOutPrizesAggregate` and delegates to `IAwardRepository.saveGiveOutPrizesAggregate`, which directly writes `user_credit_account` via `IUserCreditAccountDao`. See the Phase 2.2-B4 section for the full call chain and transaction-boundary analysis.

### 4.2 Callers of `IActivityAccountQuotaService`

| Caller | Location | Notes |
|--------|----------|-------|
| `RaffleActivityPartakeService` | `big-market-domain/activity/` | Checks and decrements quota before allowing raffle participation. Heavy coupling — must become a Dubbo RPC call. |
| `ActivityArmoryService` | `big-market-domain/activity/` | Reads account quota during armory. |

### 4.3 Direct DB Access from Other Domain Services

Run the following query to verify no infrastructure mapper touches account tables from
market-service scan scope:

```sql
-- Confirm no cross-service table access (reference only)
SELECT table_name FROM information_schema.tables
WHERE table_schema = 'big_market' AND table_name IN (
  'user_credit_account', 'user_credit_order',
  'raffle_activity_account', 'raffle_activity_account_day',
  'raffle_activity_account_month'
);
```

Grep for mappers in `big-market-infrastructure`:
```bash
grep -r "user_credit_account\|raffle_activity_account" \
  big-market-infrastructure/src/main/java --include="*.java" -l
```

---

## 5. Required Interfaces / Dubbo APIs Before Extraction

The following Dubbo service interfaces must be defined in `big-market-api` and implemented
by account-service **before** the extraction cut-over:

### 5.1 `IAccountCreditService` (Dubbo API)

```java
// big-market-api: com.dyx.market.trigger.api.IAccountCreditService
public interface IAccountCreditService {
    Response<String> createOrder(CreditTradeRequestDTO request);
    Response<BigDecimal> queryUserCreditAccount(String userId);
}
```

Replaces the current `ICreditAdjustService` for cross-service calls. The domain interface
remains for intra-service use.

### 5.2 `IAccountQuotaService` (Dubbo API)

```java
// big-market-api: com.dyx.market.trigger.api.IAccountQuotaService
public interface IAccountQuotaService {
    Response<UnpaidActivityOrderResponseDTO> createOrder(AccountQuotaCreateOrderRequestDTO request);
    Response<Boolean> updateOrder(AccountQuotaUpdateOrderRequestDTO request);
    Response<UserActivityAccountResponseDTO> queryActivityAccountEntity(Long activityId, String userId);
    Response<Integer> queryRaffleActivityAccountPartakeCount(Long activityId, String userId);
    Response<Integer> queryRaffleActivityAccountDayPartakeCount(Long activityId, String userId);
}
```

Required by market-service/message-job-service quota write paths. The high-risk raffle
partake decrement path still needs a purpose-built RPC before cutover.

---

## 5.3 Phase 2.2-B2 Write-Path Cutover Checklist

| Callsite | Type | Current local dependency | Proposed adapter | Idempotency key | Rollback switch | Risk |
|----------|------|--------------------------|------------------|-----------------|-----------------|------|
| `RebateMessageConsumer` | Write | `IRaffleActivityAccountQuotaService.createOrder` | `IAccountQuotaWriteAdapter.createOrder` | `BehaviorRebateOrderEntity.orderId` / `outBusinessNo` | `ACCOUNT_SERVICE_REMOTE_QUOTA_WRITE_ENABLED=false` | Low |
| `CreditAdjustSuccessConsumer` | Write | `IRaffleActivityAccountQuotaService.updateOrder` | `IAccountQuotaWriteAdapter.updateOrder` | `CreditAdjustSuccessMessage.outBusinessNo` | `ACCOUNT_SERVICE_REMOTE_QUOTA_WRITE_ENABLED=false` | Low |
| `SendAwardConsumer` → `UserCreditRandomAward` | Write | `IUserCreditAccountDao` directly inside `AwardRepository.saveGiveOutPrizesAggregate` (NOT `ICreditAdjustService`) | Cannot safely use `IAccountCreditWriteAdapter` yet — credit write and award-record write share one transaction; splitting creates partial-success risk | Award `orderId` (idempotency via `updateAwardRecordCompletedState` returning 0) | deferred; requires saga/outbox strategy | High |
| `RaffleActivityController.creditPayExchangeSku` | Write | `IRaffleActivityAccountQuotaService.createOrder` then `ICreditAdjustService.createOrder` | quota adapter then credit adapter | Generated quota `outBusinessNo` | both write flags false | Medium |
| `RaffleActivityPartakeService` quota decrement path | Write | `IRaffleActivityPartakeService` / quota account tables through activity domain | Purpose-built quota decrement RPC, not current scaffold | Raffle order / participation business id | new dedicated flag, plus keep local service scanned | High |
| Read endpoints already wired through `IAccountReadAdapter` | Read-only | `ICreditAdjustService` / `IRaffleActivityAccountQuotaService` fallback | `IAccountReadAdapter` | N/A | `ACCOUNT_SERVICE_REMOTE_READ_ENABLED=false` | Low |

**Recommended B2 order, lowest risk to highest:**
1. `RebateMessageConsumer` quota create-order path
2. `CreditAdjustSuccessConsumer` quota update-order path
3. `SendAwardConsumer` / `UserCreditRandomAward` credit award path
4. `RaffleActivityController.creditPayExchangeSku`
5. `RaffleActivityPartakeService` quota decrement path, only after a dedicated decrement RPC and load/rollback test

---

## 6. Migration Risks

| Risk | Severity | Mitigation |
|------|----------|------------|
| `RaffleActivityPartakeService` calls quota service synchronously during raffle — adding a Dubbo hop increases latency | High | Add Dubbo timeout + local fallback/cache; load-test before cut-over |
| `UserCreditRandomAward` / `AwardRepository` couples credit-account and award-record writes in one local transaction | High | Use transactional outbox or saga before moving credit-award writes to account-service; do not wire `IAccountCreditWriteAdapter` directly yet |
| Two JVMs writing to `raffle_activity_account` during cut-over window | High | Use a feature flag in `NacosConfigSyncService` to switch routing atomically; verify with integration test before enabling |
| Quota check and raffle participation are not in the same transaction after extraction | Medium | Accept eventual consistency; use optimistic locking (`version` column) on account tables |
| account-service has no HTTP endpoints today — no smoke test coverage | Medium | Add a `/actuator/health` check to smoke test and add one Dubbo health probe |
| Nacos Dubbo registry must be up for account-service to register | Low | This is already a dependency for market-service; no new infrastructure |

---

## 7. Step-by-Step Extraction Plan

**Gate check before starting:** `./scripts/smoke-test-phase-1.sh` returns 16/16 PASS.

### Step 1 — Add Dubbo API interfaces (in `big-market-api`)

1. Add `IAccountCreditService` to `big-market-api/src/main/java/com/dyx/market/api/`
2. Add `IActivityAccountService` to the same package
3. Add DTOs: `ActivityAccountDTO`
4. `mvn clean package -DskipTests` — must compile cleanly

### Step 2 — Create the `big-market-account-service` Maven module

1. Create `big-market-account-service/pom.xml` with deps:
   `big-market-infrastructure`, `big-market-domain`, `big-market-api`, `big-market-types`
2. Add `<module>big-market-account-service</module>` to root `pom.xml`
3. Create `AccountServiceApplication.java` with `scanBasePackages`:
   - `com.dyx.market.account.service`
   - `com.dyx.market.domain.credit`
   - `com.dyx.market.domain.activity.service.quota`
   - `com.dyx.market.infrastructure`
4. Create `src/main/resources/application.yml` (port 8086, MySQL, Redis, Dubbo)
5. Create `src/main/resources/logback-spring.xml` (copy from auth-service template)
6. Add `TraceIdFilter` (copy from any existing service)
7. Implement `IAccountCreditService` and `IActivityAccountService` as `@DubboService`

### Step 3 — Add account-service to docker-compose.yml

```yaml
big-market-account-service:
  build:
    context: .
    dockerfile: Dockerfile.service
    args:
      MODULE_NAME: big-market-account-service
  ports:
    - "8086:8086"
  environment:
    - SERVER_PORT=8086
    - MYSQL_HOST=mysql
    # ... same pattern as other services
  networks:
    - dev-ops_my-network
  healthcheck:
    test: ["CMD-SHELL", "curl -sf http://localhost:8086/actuator/health || exit 1"]
    interval: 20s
    timeout: 10s
    retries: 10
    start_period: 90s
```

### Step 4 — Wire callers through adapters / ports

1. In `CreditAdjustSuccessConsumer` / `RebateMessageConsumer` (message-job-service): route through trigger-level write adapters; remote adapters own the `@DubboReference` fields.
2. In `RaffleActivityController.creditPayExchangeSku` (market-service): route through trigger-level write adapters; remote adapters own the `@DubboReference` fields.
3. In `UserCreditRandomAward` / `AwardRepository`: do **not** inject Dubbo directly. Phase 2.2-B4 found the credit-account write shares a local transaction with `user_award_record`; add saga/outbox first.
4. In `RaffleActivityPartakeService` (domain/activity): introduce a purpose-built quota decrement RPC/port before wiring.
5. Remove `domain.credit` and `domain.activity.service.quota` from market-service scan in `MarketServiceApplication`

### Step 5 — Build and test locally

```bash
mvn clean package -DskipTests
./scripts/validate-microservices-stack.sh --skip-build
```

Smoke test must still return 16/16. Add health check for port 8086.

### Step 6 — Extend smoke test to 17+ checks

Add to `scripts/smoke-test-phase-1.sh`:
- Health check: `http://$HOST:8086/actuator/health`
- (Optional) A Dubbo probe endpoint if account-service exposes one

### Step 7 — Integration test cross-service flows

Before marking extraction complete, verify these flows end-to-end with Docker stack running:

| Flow | How to test |
|------|------------|
| Sign-in rebate credit | POST behavior rebate, confirm `user_credit_account.account_amount` increases |
| Raffle win → credit award | Draw raffle, confirm credit award issued via account-service log |
| Activity quota decrement | Draw raffle, confirm `raffle_activity_account` decremented via account-service |

---

## Phase 2.2-B — Controlled Call-site Cutover Plan

**Prerequisite:** 17/17 PASS on smoke test with Docker stack running.

### 2.2-B Step 1 — Wire credit callers to Dubbo

For each existing caller of `ICreditAdjustService`:

1. **`CreditAdjustSuccessConsumer`** (message-job-service `trigger.listener`):
   - Add `@DubboReference(version = "1.0") IAccountCreditService accountCreditService`
   - Replace `creditAdjustService.createOrder(trade)` with `accountCreditService.createOrder(dto)`
   - Map `TradeEntity` → `CreditTradeRequestDTO` before calling

2. **`UserCreditRandomAward` / `AwardRepository`** (award credit path):
   - Phase 2.2-B4 corrected the call chain: this path does not call `ICreditAdjustService`.
   - `AwardRepository.saveGiveOutPrizesAggregate` writes `user_credit_account` and `user_award_record` in one local transaction.
   - Do NOT add `@DubboReference` or `IAccountCreditWriteAdapter` directly here until saga/outbox is implemented.

3. **`SignInRebateStrategy`** (domain rebate): Same port approach as above.

### 2.2-B Step 2 — Wire quota callers to Dubbo

For `RaffleActivityPartakeService` (domain activity): introduce `IActivityAccountPort`
in the domain, implement as Dubbo call in market-service launcher. This avoids domain
coupling to the Dubbo API.

### 2.2-B Step 3 — Remove domain packages from market-service scan

After all callers are wired:
- Remove `com.dyx.market.domain.credit` from `MarketServiceApplication.scanBasePackages`
- Remove `com.dyx.market.domain.activity.service.quota` from the scan
- Run full smoke test; confirm 17/17 still passes

### 2.2-B Step 4 — Validate under load

Run at least a brief load test before removing the in-process fallback. The Dubbo hop
adds latency to the raffle critical path — benchmark and set an appropriate timeout.

### 2.2-B Step 5 — Mark Phase 2.2 complete and update roadmap

---

## 8. Rollback Criteria

Immediately revert account-service extraction (remove from scan, redeploy market-service) if:

- Smoke test drops below 16/16 after the change
- `RaffleActivityPartakeService` latency P99 exceeds 500ms (baseline is ~10ms in-process)
- Any `user_credit_account` write failures appear in logs (credit loss)
- `raffle_activity_account` quota allows more draws than permitted (quota bypass)
- market-service startup time exceeds 90s

Rollback command:
```bash
# Revert MarketServiceApplication scanBasePackages to include domain.credit + domain.activity.service.quota
# Then:
docker compose up --build -d big-market-market-service big-market-message-job-service
./scripts/smoke-test-phase-1.sh
```

---

## 9. Acceptance Criteria

Account-service extraction is considered complete when:

- [ ] `./scripts/smoke-test-phase-1.sh` returns ≥17/17 (including account-service health)
- [ ] `docker compose ps` shows all 7 services healthy
- [ ] Sign-in rebate flow verified end-to-end
- [ ] Raffle draw → credit award flow verified end-to-end
- [ ] No direct `user_credit_account` writes from market-service JVM (verify via DB connection metrics)
- [ ] Rollback procedure documented and tested in dev environment

---

## 10. Out of Scope for This Phase

- **Schema isolation**: `user_credit_account` and `raffle_activity_account*` tables stay in the shared MySQL instance. Moving them to a dedicated schema is Phase 3.
- **account-service HTTP endpoints**: No REST endpoints are planned. Dubbo-only.
- **Event sourcing for credit ledger**: The current append-only `user_credit_order` table is sufficient.
- **Credit balance caching**: Redis cache for credit balance is already handled by `CreditAdjustService` internals; no change needed.

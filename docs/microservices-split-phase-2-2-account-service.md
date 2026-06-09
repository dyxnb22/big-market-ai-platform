# Phase 2.2 — account-service Extraction Readiness Document

**Status: Phase 2.2-B validation scaffold complete. Remote-read is script-validated, defaults off. Write-path adapters/flags are prepared, defaults off, and no write callers are cut over.**

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

**What is still NOT done (Phase 2.2-B2 and beyond):**
- Write paths: credit createOrder, quota decrement, rebate, award still use local domain services in-process
- No domain packages removed from market-service scan
- No database schema changes
- No domain code relocated
- No write traffic is flowing to account-service (write feature flags default false)

**Gate check for Phase 2.2-B2 (write-path cutover):**
1. Docker 17/17 PASS: `./scripts/smoke-test-phase-1.sh` with full Docker stack
2. Remote-read validation: `./scripts/validate-account-remote-read.sh`
3. Only then proceed to write-path cutover

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
| `UserCreditRandomAward` (award strategy) | `big-market-domain/award/` | Issues credit as a prize. Calls `ICreditAdjustService` directly. Must go through Dubbo after extraction. |
| `SignInRebateStrategy` (rebate) | `big-market-domain/rebate/` | Calls `ICreditAdjustService` via `BehaviorRebateService`. Must go through Dubbo after extraction. |

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
| `SendAwardConsumer` / `UserCreditRandomAward` | Write | `ICreditAdjustService.createOrder` inside award distribution | `IAccountCreditWriteAdapter.createOrder` | Award `orderId` / credit `outBusinessNo` | `ACCOUNT_SERVICE_REMOTE_CREDIT_WRITE_ENABLED=false` | Medium |
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
| `UserCreditRandomAward` is on the award dispatch critical path — Dubbo failure during award means credit not issued | High | Implement Dubbo retries + idempotency via `orderId`; DLQ the message-job-service consumer |
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

### Step 4 — Wire callers via Dubbo (in message-job-service and domain)

1. In `CreditAdjustSuccessConsumer` (message-job-service): inject `@DubboReference IAccountCreditService` and replace direct `ICreditAdjustService` call
2. In `UserCreditRandomAward` (domain/award): inject `@DubboReference IAccountCreditService`
3. In `RaffleActivityPartakeService` (domain/activity): inject `@DubboReference IActivityAccountService`
4. Remove `domain.credit` and `domain.activity.service.quota` from market-service scan in `MarketServiceApplication`

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

2. **`UserCreditRandomAward`** (domain award strategy):
   - This is in the shared domain JAR. The cleanest approach is to introduce an
     `ICreditAwardPort` interface in the domain, implement it in each service launcher
     (market-service: in-process; account-service: Dubbo call), and inject via Spring.
   - Do NOT add `@DubboReference` directly into the domain layer.

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

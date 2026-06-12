# Phase 8 Cutover Conflict Matrix

Last revised: 2026-06-12.

Status: repo-only design artifact. Every row below remains EXTERNAL-GATED.
No remote, outbox, or cutover flag defaults to `true` in this repository.

## Purpose

This matrix maps every active legacy/local adapter, RPC provider, fallback
port, and shared task dispatcher against its intended future remote/outbox
replacement. The primary Phase 8 safety rule is:

> The legacy path and the future path must never both be active in the same
> environment outside a controlled canary window.

Each row documents the exact flag pair that controls the two paths and the
evidence gates required before the legacy path can be disabled or removed.

This document is the authoritative conflict reference for
`scripts/validate-microservices-phase-8-cutover-conflict-matrix.sh`.

---

## 1. Account / Credit Write

| Field | Value |
|-------|-------|
| **Legacy path** | `LocalAccountCreditWriteAdapter` (`big-market-trigger/src/main/java/.../trigger/adapter/LocalAccountCreditWriteAdapter.java`) |
| **Future path** | Account-service Dubbo provider via `IAccountCreditWriteAdapter`; market-service uses an always-registered wrapper that falls back locally when the flag is false, while message-job-service uses a flag-conditional remote bean |
| **Owning service** | `big-market-account-service` |
| **Flag that enables new path** | `account.service.remote-credit-write.enabled` / `ACCOUNT_SERVICE_REMOTE_CREDIT_WRITE_ENABLED` |
| **Flag that disables old path** | Same flag — message-job-service activates the remote bean when `true` and suppresses the local bean via `@ConditionalOnMissingBean`; market-service keeps a wrapper bean and switches the remote branch internally |
| **Tables affected** | `user_credit_account`, `user_credit_order`, future `credit_trade_task_outbox` |
| **Why both must not run simultaneously** | Double credit issuance — each path would independently call `createOrder`, duplicating credit transactions. The account-service idempotency guard (`outBusinessNo`) mitigates but does not eliminate the risk of duplicate DB writes. |
| **Current safe default** | `account.service.remote-credit-write.enabled=false` — local write semantics remain active; any wrapper bean falls through to `ICreditAdjustService` |
| **Evidence required before enabling remote** | EXTERNAL-GATED: account credit write staging/prod evidence, no credit drift, rollback rehearsal |
| **7-day stable gate** | EXTERNAL-GATED: 7 clean days with remote credit write enabled |
| **30-day removal gate** | EXTERNAL-GATED: 30 clean days plus cleanup signoff before removing local adapter |

## 2. Account / Quota Write

| Field | Value |
|-------|-------|
| **Legacy path** | `LocalAccountQuotaWriteAdapter` (`big-market-trigger/src/main/java/.../trigger/adapter/LocalAccountQuotaWriteAdapter.java`) |
| **Future path** | Account-service Dubbo provider for quota write operations; market-service uses an always-registered wrapper with local fallback, while message-job-service uses a flag-conditional remote bean |
| **Owning service** | `big-market-account-service` |
| **Flag that enables new path** | `account.service.remote-quota-write.enabled` / `ACCOUNT_SERVICE_REMOTE_QUOTA_WRITE_ENABLED` |
| **Flag that disables old path** | Same flag — message-job-service uses remote `@ConditionalOnProperty` plus local `@ConditionalOnMissingBean`; market-service switches the remote branch inside the wrapper |
| **Tables affected** | `raffle_activity_account`, `raffle_activity_account_day`, `raffle_activity_account_month` |
| **Why both must not run simultaneously** | Double quota allocation — both local and remote paths would update the same account rows, causing quota inflation. |
| **Current safe default** | `account.service.remote-quota-write.enabled=false` |
| **Evidence required before enabling remote** | EXTERNAL-GATED: quota write staging/prod evidence, no quota drift, rollback rehearsal |
| **7-day stable gate** | EXTERNAL-GATED |
| **30-day removal gate** | EXTERNAL-GATED |

## 3. Account / Quota Decrement (Draw Path)

| Field | Value |
|-------|-------|
| **Legacy path** | `LocalActivityAccountPort` (`big-market-infrastructure/src/main/java/.../infrastructure/adapter/port/LocalActivityAccountPort.java`) — `@ConditionalOnProperty(matchIfMissing=true)` |
| **Future path** | `AccountRemoteActivityAccountPort` (`big-market-market-service/src/main/java/.../market/config/AccountRemoteActivityAccountPort.java`) — `@ConditionalOnProperty(havingValue="true")` |
| **Owning service** | `big-market-account-service` |
| **Flag that enables new path** | `account.service.remote-quota-decrement.enabled` / `ACCOUNT_SERVICE_REMOTE_QUOTA_DECREMENT_ENABLED` |
| **Flag that disables old path** | Same flag — when `true`, the remote port activates and the local port's `havingValue="false", matchIfMissing=true` condition no longer matches |
| **Tables affected** | `raffle_activity_account`, `raffle_activity_account_day`, `raffle_activity_account_month`, `raffle_quota_decrement_ledger` |
| **Idempotency key** | `raffle_quota_decrement_ledger.uq_user_activity_biz (user_id, activity_id, out_business_no)` |
| **Why both must not run simultaneously** | Double quota decrement in the draw hot path — each path would independently decrement the quota, potentially allowing a user to exceed their raffle entry limit. The ledger UNIQUE key prevents double-decrement within a single path but does not protect against dual-path execution. |
| **Current safe default** | `account.service.remote-quota-decrement.enabled=false` — local port active, remote port not instantiated |
| **Evidence required before enabling remote** | EXTERNAL-GATED: quota decrement ledger DDL applied, staging/prod idempotency evidence, no quota exhaustion drift |
| **7-day stable gate** | EXTERNAL-GATED |
| **30-day removal gate** | EXTERNAL-GATED |

## 4. Fulfillment / Award Dispatch

| Field | Value |
|-------|-------|
| **Legacy path** | `LocalAwardDispatchAdapter` (`big-market-trigger/src/main/java/.../trigger/adapter/LocalAwardDispatchAdapter.java`) |
| **Future path** | Fulfillment-service Dubbo provider for award fulfillment |
| **Owning service** | `big-market-fulfillment-service` |
| **Flag that enables new path** | `account.fulfillment.remote-award.enabled` / `ACCOUNT_FULFILLMENT_REMOTE_AWARD_ENABLED` |
| **Flag that disables old path** | Same flag — `WriteAdapterLocalConfig` registers the remote bean when `true`; the local `@ConditionalOnMissingBean` path is suppressed when that remote bean exists |
| **Tables affected** | `award`, `user_award_record`, `award_dispatch_task_outbox` |
| **Why both must not run simultaneously** | Duplicate award records — the user could receive the same award twice if both local and remote dispatch paths execute. The award_dispatch_task_outbox UNIQUE key mitigates within a single path. |
| **Current safe default** | `account.fulfillment.remote-award.enabled=false` |
| **Evidence required before enabling remote** | EXTERNAL-GATED: fulfillment cutover evidence, no missing/duplicate awards, rollback rehearsal |
| **7-day stable gate** | EXTERNAL-GATED |
| **30-day removal gate** | EXTERNAL-GATED |

## 5. Fulfillment / Award (Draw Hot Path)

| Field | Value |
|-------|-------|
| **Legacy path** | `LocalAwardFulfillmentPort` (`big-market-infrastructure/src/main/java/.../infrastructure/adapter/port/LocalAwardFulfillmentPort.java`) — `@ConditionalOnProperty(matchIfMissing=true)` |
| **Future path** | Remote award fulfillment port (not yet implemented — gated behind activity draw cutover) |
| **Owning service** | `big-market-fulfillment-service` |
| **Flag that enables new path** | No remote flag enabled; draw orchestration stays in market-service (Phase 5-G design-ready) |
| **Flag that disables old path** | Activity draw cutover remains EXTERNAL-GATED |
| **Tables affected** | `user_award_record`, `user_raffle_order` |
| **Why both must not run simultaneously** | The draw hot path creates award records synchronously during raffle execution. A dual path would double-issue awards and potentially corrupt raffle order state. This is the highest-risk cutover in Phase 8. |
| **Current safe default** | Local port only; no remote draw fulfillment exists |
| **Evidence required before enabling remote** | EXTERNAL-GATED: Product, DBA, Ops, Engineering, and Oncall activity draw cutover evidence |
| **7-day stable gate** | EXTERNAL-GATED |
| **30-day removal gate** | EXTERNAL-GATED |

## 6. Rebate / Create Order

| Field | Value |
|-------|-------|
| **Legacy path (provider)** | `RebateServiceRPC` (`big-market-trigger/src/main/java/.../trigger/rpc/RebateServiceRPC.java`) — legacy Dubbo provider, `@ConditionalOnProperty(matchIfMissing=true)` |
| **Legacy path (adapter)** | `LocalRebateOrderAdapter` (`big-market-trigger/src/main/java/.../trigger/adapter/LocalRebateOrderAdapter.java`) |
| **Future path** | Rebate-service Dubbo provider |
| **Owning service** | `big-market-rebate-service` |
| **Flag that enables new path** | `rebate.service.remote-create-order.enabled` / `REBATE_SERVICE_REMOTE_CREATE_ORDER_ENABLED` |
| **Flag that disables old path (provider)** | `rebate.legacy-rpc-provider.enabled` / `REBATE_LEGACY_RPC_PROVIDER_ENABLED` |
| **Tables affected** | `daily_behavior_rebate`, `user_behavior_rebate_order`, future `rebate_task_outbox` |
| **Dual-provider risk** | If `REBATE_LEGACY_RPC_PROVIDER_ENABLED=true` AND `REBATE_SERVICE_REMOTE_CREATE_ORDER_ENABLED=true`, TWO Dubbo providers for `IRebateService` would register simultaneously, causing undefined routing behavior. |
| **Current safe default** | `rebate.legacy-rpc-provider.enabled=true` (legacy provider active), `rebate.service.remote-create-order.enabled=false` (remote not active). These must never both be true outside a controlled canary. |
| **Evidence required before enabling remote** | EXTERNAL-GATED: rebate-service DBA/Ops/Engineering/Oncall/Product evidence, legacy provider disabled first, rebate outbox DDL evidence |
| **7-day stable gate** | EXTERNAL-GATED: 7 clean days after remote rebate write/read cutover |
| **30-day removal gate** | EXTERNAL-GATED: 30 clean days plus cleanup signoff |

## 7. Rebate / Read

| Field | Value |
|-------|-------|
| **Legacy path** | `LocalRebateReadAdapter` (`big-market-trigger/src/main/java/.../trigger/adapter/LocalRebateReadAdapter.java`) |
| **Future path** | Rebate-service Dubbo provider for read operations via an always-registered adapter wrapper |
| **Owning service** | `big-market-rebate-service` |
| **Flag that enables new path** | `rebate.service.remote-read.enabled` / `REBATE_SERVICE_REMOTE_READ_ENABLED` |
| **Flag that disables old path** | Same flag controls the wrapper's remote branch; when `false`, the wrapper falls through to local `IBehaviorRebateService` |
| **Tables affected** | `daily_behavior_rebate`, `user_behavior_rebate_order` |
| **Why both must not run simultaneously** | Read inconsistency — callers would receive different results depending on which path the RPC framework routes to. |
| **Current safe default** | `rebate.service.remote-read.enabled=false` |
| **Evidence required before enabling remote** | EXTERNAL-GATED: rebate read parity and latency evidence |
| **7-day stable gate** | EXTERNAL-GATED |
| **30-day removal gate** | EXTERNAL-GATED |

## 8. Strategy / Read

| Field | Value |
|-------|-------|
| **Legacy path (provider)** | `RaffleStrategyServiceRPC` (`big-market-trigger/src/main/java/.../trigger/rpc/RaffleStrategyServiceRPC.java`) — legacy Dubbo provider, `@ConditionalOnProperty(matchIfMissing=true)` |
| **Legacy path (adapter)** | `LocalStrategyReadAdapter` (`big-market-trigger/src/main/java/.../trigger/adapter/LocalStrategyReadAdapter.java`) |
| **Future path** | Strategy-service Dubbo provider for read operations via an always-registered adapter wrapper |
| **Owning service** | `big-market-strategy-service` |
| **Flag that enables new path** | `strategy.service.remote-read.enabled` / `STRATEGY_SERVICE_REMOTE_READ_ENABLED` |
| **Flag that disables old path (provider)** | `strategy.legacy-rpc-provider.enabled` / `STRATEGY_LEGACY_RPC_PROVIDER_ENABLED` |
| **Tables affected** | `strategy`, `strategy_award`, `strategy_rule`, `rule_tree`, `rule_tree_node`, `rule_tree_node_line` |
| **Dual-provider risk** | If `STRATEGY_LEGACY_RPC_PROVIDER_ENABLED=true` AND `STRATEGY_SERVICE_REMOTE_READ_ENABLED=true`, TWO Dubbo providers for `IRaffleStrategyService` would register simultaneously. |
| **Current safe default** | `strategy.legacy-rpc-provider.enabled=true` (legacy provider active), `strategy.service.remote-read.enabled=false` (remote not active). These must never both be true outside a controlled canary. |
| **Evidence required before enabling remote** | EXTERNAL-GATED: strategy-service Ops provider listing, Engineering read parity, Oncall monitoring, Product evidence or exemption |
| **7-day stable gate** | EXTERNAL-GATED: 7 clean days after strategy remote-read cutover |
| **30-day removal gate** | EXTERNAL-GATED: 30 clean days plus cleanup signoff |

## 9. Shared Task / Outbox Dispatcher (SendMessageTaskJob)

| Field | Value |
|-------|-------|
| **Legacy path** | `SendMessageTaskJob` (`big-market-trigger/src/main/java/.../trigger/job/SendMessageTaskJob.java`) — scans shared `task` table and dispatches via MQ |
| **Legacy fallback ports** | `LocalRebateTaskOutboxPort`, `LocalCreditTradeTaskOutboxPort`, `LocalAwardDispatchTaskOutboxPort` (all in `big-market-infrastructure/.../adapter/port/`) — each delegates to `ITaskDao` for the shared `task` table |
| **Future path (credit-award)** | `DispatchCreditAwardTaskJob` (`big-market-message-job-service/.../job/config/DispatchCreditAwardTaskJob.java`) — scans `credit_award_task` outbox tables, `@ConditionalOnProperty(name="account.award-credit-outbox.enabled", havingValue="true")` |
| **Future path (rebate)** | Per-domain `rebate_task_outbox` dispatcher (future) |
| **Future path (credit trade)** | Per-domain `credit_trade_task_outbox` dispatcher (future) |
| **Future path (award dispatch)** | Per-domain `award_dispatch_task_outbox` dispatcher (future) |
| **Owning services** | Per-domain owners (account-service, rebate-service, fulfillment-service) |
| **Flag that enables per-domain outbox** | `account.award-credit-outbox.enabled` / `ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED` (credit-award); future flags for rebate/credit-trade/award-dispatch |
| **Flag that disables shared fallback for a domain** | `job.shared-task-fallback.credit-award-disabled` / future per-domain flags |
| **Tables affected** | `task` (shared legacy), `credit_award_task` (per-domain outbox), future `rebate_task_outbox`, `credit_trade_task_outbox`, `award_dispatch_task_outbox` |
| **Dual-dispatch risk** | When `account.award-credit-outbox.enabled=true` AND `job.shared-task-fallback.credit-award-disabled=false`, both `DispatchCreditAwardTaskJob` (per-domain) and `SendMessageTaskJob` (shared) could process the same credit-award work item from two different tables/paths, causing duplicate credit issuance. |
| **Idempotency key (credit-award)** | `credit_award_task.uq_award_order_id (user_id, award_order_id)` — ensures a given award order produces at most one outbox row; the INSERT fails with DuplicateKeyException on retry, which the caller treats as an already-processed event. |
| **Idempotency key (quota decrement)** | `raffle_quota_decrement_ledger.uq_user_activity_biz (user_id, activity_id, out_business_no)` — ensures one ledger row per (user, activity, business operation); the INSERT inside the transaction fails with DuplicateKeyException on retry, and the caller returns true immediately. |
| **Current safe default** | `account.award-credit-outbox.enabled=false` — `DispatchCreditAwardTaskJob` is not instantiated (bean conditional). Shared `SendMessageTaskJob` and local fallback ports are active for all domains. |
| **Evidence required before enabling per-domain outbox** | EXTERNAL-GATED: per-domain outbox DDL applied, dispatch evidence, no duplicate/pending drain, all legacy fallback ports for that domain explicitly disabled |
| **7-day stable gate** | EXTERNAL-GATED: 7 clean days with per-domain outbox active |
| **30-day removal gate** | EXTERNAL-GATED: 30 clean days plus cleanup signoff before removing shared `task` fallback |

---

## Flag Conflict Summary

| Domain | Legacy active when | Future active when | Must NOT both be true |
|--------|---------------------|--------------------|-----------------------|
| account credit write | flag=false (default) | flag=true | N/A — same flag controls both |
| account quota write | flag=false (default) | flag=true | N/A — same flag controls both |
| account quota decrement | flag=false (default) | flag=true | N/A — same flag controls both |
| fulfillment award | flag=false (default) | flag=true | N/A — same flag controls both |
| rebate create-order | `REBATE_LEGACY_RPC_PROVIDER_ENABLED=true` | `REBATE_SERVICE_REMOTE_CREATE_ORDER_ENABLED=true` | **Both flags true = dual-provider risk** |
| rebate read | flag=false (default) | flag=true | N/A — same flag controls both |
| strategy read | `STRATEGY_LEGACY_RPC_PROVIDER_ENABLED=true` | `STRATEGY_SERVICE_REMOTE_READ_ENABLED=true` | **Both flags true = dual-provider risk** |
| shared task dispatcher | outbox flag=false (default) | outbox flag=true AND shared-fallback NOT disabled | **Outbox enabled + shared not disabled = dual-dispatch risk** |

---

## Cross-References

- Legacy cleanup inventory: `docs/microservices-legacy-cleanup-inventory.md`
- DAO ownership matrix: `docs/microservices-dao-ownership.md`
- Phase 8 cutover runbook: `docs/microservices-phase-8-cutover-runbook.md`
- Phase 8 runtime safety validator: `scripts/validate-microservices-phase-8-runtime-safety.sh`
- This matrix's validator: `scripts/validate-microservices-phase-8-cutover-conflict-matrix.sh`

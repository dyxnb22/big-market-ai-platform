# Java deletion ledger (safe removals only)

**Rule:** Delete a Java path only when Rust (or remaining Java embedded path still required for legacy compose) covers the **demo/learning capability**.  
Full rule-tree engine / XXL Admin / Nacos / OpenAI / Dubbo stay until explicitly replaced.

**Date:** 2026-07-16  
**Batch:** 1 — optional dedicated launchers (never in default `docker-compose.yml`)

## Batch 1 — deleted

### `big-market-rebate-service` (optional :8088)

| Deleted path | Rust (or remaining) equivalent | Why safe |
| --- | --- | --- |
| `…/RebateServiceApplication.java` | `bm-app` process | Bootstrap only |
| `…/provider/RebateServiceRPC.java` | `bm_domain::RebateService` + HTTP `calendar_sign_rebate_by_token` / `is_calendar_sign_rebate_by_token`; Java embedded remains in `big-market-trigger/.../RebateServiceRPC.java` | Dedicated Dubbo host duplicate of embedded market provider |
| `…/config/PrometheusConfiguration.java` | `bm-app` `/metrics` | Metrics wiring |
| `…/RebateServiceApplicationScanTest.java` | n/a (launcher test) | Module gone |
| Mapper XML copies (`daily_behavior_rebate_*`, `task_*`, `user_behavior_rebate_order_*`) | `bm-infra` mysql rebate/outbox; originals remain under infrastructure / market-service | Process-local MyBatis copies only |
| `application*.yml`, `logback*`, `spring-config*`, `pom.xml` | Rust env / `AppConfig` | Config for deleted process |

### `big-market-strategy-service` (optional :8089)

| Deleted path | Rust (or remaining) equivalent | Why safe |
| --- | --- | --- |
| `…/StrategyServiceApplication.java` | `bm-app` | Bootstrap only |
| `…/provider/StrategyReadServiceRPC.java` | HTTP `query_raffle_award_list_by_token` + `query_raffle_strategy_rule_weight_by_token`; Java embedded `LocalStrategyReadAdapter` remains | Dedicated Dubbo host duplicate |
| `…/application/StrategyReadApplicationService.java` | `award_lock_view` + `rule_weight_list_views` + `ParticipationStore` / `StrategyStore` | Same read models on Rust HTTP |
| `…/port/IStrategyAccountParticipationPort.java` | `ParticipationStore` | Port |
| `…/port/LocalStrategyAccountParticipationPort.java` | `mysql_participation` / memory `count_draws` | Adapter |
| `…/StrategyServiceApplicationScanTest.java` | n/a | Module gone |
| Mapper XML copies (`strategy_*`, `rule_tree_*`) | sqlx `mysql_strategy` (+ award list); **rule-tree engine code stays in `big-market-domain`** | Copies only — engine not deleted |
| `application*.yml`, `logback*`, `spring-config*`, `pom.xml` | Rust env | Config for deleted process |

## Explicitly NOT deleted (Batch 1)

| Keep | Reason |
| --- | --- |
| `big-market-domain/**/strategy/service/rule/tree/**` | Full rule-tree **not** in Rust lite |
| `big-market-domain/**/rebate/**` | Still used by Java market embedded + learning对照 |
| `big-market-trigger` strategy/rebate adapters & HTTP | Default Java compose still boots market |
| All other launchers (gateway/auth/admin/…) | Still on Java compose / rollback path |

## Next batches (not started)

| Batch | Prerequisite | Candidates |
| --- | --- | --- |
| 2 | Retire Java compose from CI/rollback OR prove each file | Thin launchers with full HTTP parity (`auth-service`, …) |
| 3 | Dual-stack dropped | Remaining Spring Boot services |
| 4 | Explicit “drop Java learning depth” | Rule-tree / XXL / Nacos / Dubbo / OpenAI (archive or delete) |

## Verification

```bash
./scripts/acceptance-rust.sh
# parent POM no longer lists rebate/strategy modules
```

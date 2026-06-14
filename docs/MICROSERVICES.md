# Microservices Architecture

Last revised: 2026-06-14.

This repository is a complete microservices learning and portfolio project for
the Big Market raffle platform. It presents the system as the final local
learning architecture: gateway routing, independently deployable Spring Boot
services, Dubbo/Nacos service contracts, RabbitMQ message handling, XXL-Job
tasks, MySQL persistence, Redis caching, and Prometheus/Grafana observability.

This file is the authoritative entry point for the current architecture. Older
implementation notes are kept only as historical archive material under
`docs/archive/`.

## Services

| Service | Port | Current state | Responsibility |
| --- | ---: | --- | --- |
| `big-market-gateway` | 8080 | Stable, enabled | API gateway, route predicates, trace id propagation, Resilience4j fallback responses |
| `big-market-auth-service` | 8081 | Stable, enabled | Login, JWT issuing, token verification, logout revocation |
| `big-market-admin-service` | 8082 | Stable, enabled | Admin configuration APIs and Nacos configuration synchronization |
| `big-market-market-service` | 8083 | Stable, enabled | Core raffle HTTP APIs, activity operations, ERP/DCC endpoints, local domain orchestration |
| `big-market-chatbot-service` | 8084 | Stable, enabled | Chatbot API, platform config consumption, credit charge/refund integration |
| `big-market-message-job-service` | 8085 | Stable, enabled | RabbitMQ consumers, XXL-Job handlers, task retry, outbox dispatch |
| `big-market-account-service` | 8086 | Stable, owned | Credit account, credit trade, activity quota, quota ledger RPC contracts |
| `big-market-fulfillment-service` | 8087 | Stable, owned | Award fulfillment RPC, award record completion, award-credit outbox integration |
| `big-market-rebate-service` | 8088 | Stable, owned | Behavior rebate create/read RPC contracts and rebate task ownership |
| `big-market-strategy-service` | 8089 | Stable, owned | Strategy read RPC, award list reads, rule-weight reads, account participation reads |

Shared modules such as `big-market-domain`, `big-market-infrastructure`,
`big-market-api`, `big-market-types`, and the starter modules are library
dependencies used by the service launchers.

## Core Flows

### Raffle

`big-market-gateway` routes `/api/v1/raffle/**` to
`big-market-market-service`. `RaffleActivityController.draw_by_token` validates
JWT user context, `RaffleApplicationService.executeDraw` creates or reuses a
partake order, the activity domain consumes quota, the strategy domain selects
an award, and the award domain writes `user_award_record` plus a message task.

Code paths:

- `big-market-trigger/src/main/java/com/dyx/market/trigger/http/RaffleActivityController.java`
- `big-market-domain/src/main/java/com/dyx/market/domain/activity/application/RaffleApplicationService.java`
- `big-market-domain/src/main/java/com/dyx/market/domain/activity/service/partake`
- `big-market-domain/src/main/java/com/dyx/market/domain/strategy/service`
- `big-market-infrastructure/src/main/java/com/dyx/market/infrastructure/adapter/repository/AwardRepository.java`

### Account Quota

Activity quota is owned by account-oriented ports and repositories. The local
learning default keeps quota decrement and order creation transactionally close
to the draw path, while `big-market-account-service` exposes the service
contract used for quota decrement, rollback, and account reads.

Code paths:

- `big-market-api/src/main/java/com/dyx/market/trigger/api/IAccountQuotaService.java`
- `big-market-account-service/src/main/java/com/dyx/market/account/provider/AccountQuotaServiceRPC.java`
- `big-market-domain/src/main/java/com/dyx/market/domain/activity/adapter/port/IActivityAccountPort.java`
- `big-market-infrastructure/src/main/java/com/dyx/market/infrastructure/adapter/repository/ActivityRepository.java`

### Credit

Credit balance and credit trade orders support sign-in rewards, SKU exchange,
chatbot charging/refunds, and award-credit dispatch. Idempotency is handled by
business numbers such as `out_business_no`, `award_order_id`, and task message
ids.

Code paths:

- `big-market-api/src/main/java/com/dyx/market/trigger/api/IAccountCreditService.java`
- `big-market-account-service/src/main/java/com/dyx/market/account/provider/AccountCreditServiceRPC.java`
- `big-market-domain/src/main/java/com/dyx/market/domain/credit`
- `big-market-infrastructure/src/main/java/com/dyx/market/infrastructure/adapter/repository/CreditRepository.java`

### Award Fulfillment

The draw path writes an award record and publishes `send_award`. The
message-job service consumes the event and calls the award domain to issue
credits or external quota, then marks the award record complete.

Code paths:

- `big-market-trigger/src/main/java/com/dyx/market/trigger/listener/SendAwardConsumer.java`
- `big-market-domain/src/main/java/com/dyx/market/domain/award/service/AwardService.java`
- `big-market-fulfillment-service/src/main/java/com/dyx/market/fulfillment/provider/FulfillmentAwardServiceRPC.java`

### Rebate

Sign-in creates a behavior rebate order and task, publishes `send_rebate`, and
the consumer grants credit or activity quota. Rebate ownership is represented by
`big-market-rebate-service` RPC contracts and local task/outbox ports.

Code paths:

- `big-market-domain/src/main/java/com/dyx/market/domain/rebate/service/BehaviorRebateService.java`
- `big-market-rebate-service/src/main/java/com/dyx/market/rebate/provider/RebateServiceRPC.java`
- `big-market-trigger/src/main/java/com/dyx/market/trigger/listener/RebateMessageConsumer.java`

### Strategy Reads

Strategy reads expose award lists, rule weights, and account participation
signals. Market HTTP controllers use `IStrategyReadAdapter`; the strategy
service provides the RPC implementation.

Code paths:

- `big-market-trigger/src/main/java/com/dyx/market/trigger/http/RaffleStrategyController.java`
- `big-market-trigger/src/main/java/com/dyx/market/trigger/adapter/IStrategyReadAdapter.java`
- `big-market-strategy-service/src/main/java/com/dyx/market/strategy/provider/StrategyReadServiceRPC.java`

### Messages And Jobs

RabbitMQ topics carry award, rebate, credit-adjust, and stock-zero events.
XXL-Job handlers retry task rows and asynchronously flush stock counters.

Code paths:

- `big-market-message-job-service/src/main/java/com/dyx/market/message/job/config/DispatchCreditAwardTaskJob.java`
- `big-market-trigger/src/main/java/com/dyx/market/trigger/job/SendMessageTaskJob.java`
- `big-market-trigger/src/main/java/com/dyx/market/trigger/job/UpdateActivitySkuStockJob.java`
- `big-market-trigger/src/main/java/com/dyx/market/trigger/job/UpdateAwardStockJob.java`
- `big-market-message-job-service/src/main/java/com/dyx/market/message/job/config/RabbitMQDlqConfig.java`

## Data And Ownership

The service boundary matrix lives in `docs/microservices-dao-ownership.md`.
DDL and outbox learning notes live in `docs/data-and-outbox.md` and
`docs/sql/`. The SQL files are learning-environment DDL references and are not
automatically applied by this repository.

## Local Completion Standard

For this learning environment the architecture is considered complete when:

- `mvn clean package -DskipTests` succeeds.
- `./scripts/validate-microservices-stack.sh` succeeds in a local Docker
  environment.
- `./scripts/validate-microservices-runtime-safety.sh` succeeds as a
  final-architecture guardrail validator.
- Core flows can be explained from controller to domain service, repository,
  MQ/XXL-Job, and rollback/idempotency handling.
- `docs/learning/*`, this architecture document, and code/config comments tell
  the same final-state story.

## Production Disclaimer

This project is a learning environment and does not include a real production
canary or observation period.

## Documentation Index

- `docs/learning/README.md` — final-state learning guide
- `docs/production-readiness-learning.md` — learning-version readiness notes
- `docs/operations-checklist.md` — local operations checklist
- `docs/data-and-outbox.md` — data, outbox, idempotency, and duplicate handling
- `docs/microservices-dao-ownership.md` — table and DAO ownership matrix
- `docs/old-path-cleanup-inventory.md` — old-path cleanup notes
- `docs/archive/microservices-historical-readiness-notes.md` — archived historical notes, not current state
- `docs/archive/evidence-template-archive.md` — archived evidence template, not current state

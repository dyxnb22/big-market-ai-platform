# 04 Module And Service Boundaries

## Service Modules

| Module | Boundary |
| --- | --- |
| `big-market-gateway` | Gateway routing, trace propagation, response fallback |
| `big-market-auth-service` | Login, token verification, logout revocation |
| `big-market-admin-service` | Platform configuration APIs |
| `big-market-market-service` | Raffle/activity HTTP API and local orchestration |
| `big-market-chatbot-service` | Chatbot API and credit charge/refund integration |
| `big-market-message-job-service` | MQ consumers, XXL-Job handlers, retry dispatch |
| `big-market-account-service` | Credit and quota RPC provider |
| `big-market-fulfillment-service` | Award fulfillment RPC provider |
| `big-market-rebate-service` | Rebate create/read RPC provider |
| `big-market-strategy-service` | Strategy read RPC provider |

## Shared Libraries

- `big-market-trigger`: HTTP controllers, RabbitMQ listeners, and XXL-Job
  handlers. Scanned by `market-service` and `message-job-service`; not a
  standalone deployable service.
- `big-market-domain`: activity, strategy, award, credit, rebate, auth, and task
  domain models/services/ports.
- `big-market-infrastructure`: MyBatis DAOs, repository adapters, Redis, ES,
  MQ publishing, and local port implementations.
- `big-market-api`: Dubbo API contracts and DTOs.
- `big-market-types`: common response codes, exceptions, annotations, and
  constants.
- `big-market-management`: platform configuration helpers consumed by
  `admin-service` and `chatbot-service` (`PlatformConfigService`).
- Starter modules: DB router, DCC, and rate limiter.

## Legacy / Non-Primary Modules

- `big-market-app`: historical monolith launcher. The current learning path uses
  the 10 microservice launchers above; keep this module only for legacy tests
  or historical comparison.

## Boundary Rules

Service APIs are kept in `big-market-api`. Domain ports isolate cross-domain
calls. Repository adapters hide MyBatis DAOs behind domain interfaces. Shared
library reuse is intentional for this learning project and keeps the local
portfolio stack compact while still showing service ownership.

Key files:

- `pom.xml`
- `docs/microservices-dao-ownership.md`
- `big-market-domain/src/main/java/com/dyx/market/domain/activity/adapter/port`
- `big-market-domain/src/main/java/com/dyx/market/domain/award/adapter/port`
- `big-market-domain/src/main/java/com/dyx/market/domain/credit/adapter/port`
- `big-market-domain/src/main/java/com/dyx/market/domain/rebate/adapter/port`
- `big-market-domain/src/main/java/com/dyx/market/domain/strategy/adapter/port`

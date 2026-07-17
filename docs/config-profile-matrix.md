# Config Profile Matrix

Maps Spring profiles and compose overlays used by this learning stack. Prefer **code + YAML** over stale docs when they disagree.

| Profile / overlay | How activated | Intended use | Credentials / guards | Notes |
| --- | --- | --- | --- | --- |
| **local** | `SPRING_PROFILES_ACTIVE=local` (where `application-local.yml` exists: market, message-job, fulfillment, …) | IDE / single-service debug against local MySQL/Redis/Nacos | Learning defaults; do not treat as secure | Not every service ships `application-local.yml` |
| **dev** | `application-dev.yml` | Legacy/dev-style local wiring | Permissive | Prefer `local` or `docker` for documented paths |
| **test** | Surefire / `@SpringBootTest` / test resources | Unit & Context tests without full infra | Mocks / embedded / disabled remote clients | Must not write tracked `data/log` or `~/.dubbo` (governance P0) |
| **docker** | Compose `SPRING_PROFILES_ACTIVE=docker` | Full microservices stack via `docker-compose.yml` + env compose | Demo defaults allowed | Default learning path; pair with `docs/dev-ops/docker-compose-environment.yml` |
| **secure** | `docker-compose.secure.yml` overlay + `application-secure.yml` | Production-like demo | Non-default `JWT_SECRET`, RPC/admin/chat/XXL tokens; `DefaultCredentialGuard` refuses weak defaults | Run `./scripts/acceptance.sh --secure` / `smoke-security.sh` |

## Compose files

| File | Role |
| --- | --- |
| `docs/dev-ops/docker-compose-environment.yml` | Shared infra (Nacos, MySQL, Redis, RabbitMQ, XXL, Prometheus, …) |
| `docker-compose.yml` | Default application services (8080-8086) on `dev-ops_my-network` |
| `docker-compose.providers.yml` | Optional dedicated providers (fulfillment/rebate/strategy: 8087-8089) |
| `docker-compose.secure.yml` | Secure overlay for default and optional provider services (profiles + env requirements) |

## Quick commands

```bash
# Infra + apps (learning / docker profile)
docker compose -f docs/dev-ops/docker-compose-environment.yml up -d
docker compose up --build -d

# Secure overlay
docker compose -f docker-compose.yml -f docker-compose.secure.yml up --build -d

# Optional provider mode; helper recreates market/message-job consumers as needed
./scripts/start-provider-mode.sh fulfillment
./scripts/start-provider-mode.sh rebate-strategy
# Add --secure after exporting the required secure variables for secure mode.
```

See also: `docs/operations-checklist.md`, `docs/MICROSERVICES.md`.

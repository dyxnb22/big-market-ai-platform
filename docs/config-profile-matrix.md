# Config Profile Matrix

Maps Spring profiles and compose overlays used by this learning stack. Prefer **code + YAML** over stale docs when they disagree.

| Profile / overlay | How activated | Intended use | Credentials / guards | Notes |
| --- | --- | --- | --- | --- |
| **local** | `SPRING_PROFILES_ACTIVE=local` (where `application-local.yml` exists) | IDE / single-service debug against local MySQL/Redis/Nacos | Learning defaults; do not treat as secure | Not every service ships `application-local.yml` |
| **dev** | `application-dev.yml` | Legacy/dev-style local wiring | Permissive | Prefer `local` or `docker` for documented paths |
| **test** | Surefire / `@SpringBootTest` / test resources | Unit & Context tests without full infra | Mocks / local adapters / disabled account remote clients | Must not write tracked `data/log` or `~/.dubbo` (governance P0) |
| **docker** | Compose `SPRING_PROFILES_ACTIVE=docker` | Full microservices stack via `docker-compose.yml` + env compose | Demo defaults allowed | Default learning path; pair with `docs/dev-ops/docker-compose-environment.yml` |
| **secure** | `docker-compose.secure.yml` overlay + `application-secure.yml` | Production-like demo | Non-default `JWT_SECRET`, RPC/admin/chat/XXL tokens; `DefaultCredentialGuard` refuses weak defaults | Run `./scripts/acceptance.sh --secure` / `smoke-security.sh` |

## Compose files

| File | Role |
| --- | --- |
| `docs/dev-ops/docker-compose-environment.yml` | Shared infra (Nacos, MySQL, Redis, RabbitMQ, XXL, Prometheus, …) |
| `docker-compose.yml` | Default application services (8080-8086) on `dev-ops_my-network` |
| `docker-compose.secure.yml` | Secure overlay for the seven application services (profiles + env requirements) |

## Quick commands

```bash
# Infra + apps (learning / docker profile)
docker compose -f docs/dev-ops/docker-compose-environment.yml up -d mysql redis rabbitmq nacos xxl-job-admin elasticsearch
docker compose up --build -d

# Secure overlay
docker compose -f docker-compose.yml -f docker-compose.secure.yml up --build -d

```

See also: `docs/operations-checklist.md`, `docs/MICROSERVICES.md`.

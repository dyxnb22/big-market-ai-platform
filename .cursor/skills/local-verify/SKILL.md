---
name: local-verify
description: >-
  Chooses and runs Big Market local verification (Maven tests, Docker stack
  scripts, smoke API, Playwright). Use after code changes, before claiming a
  BM fixed or closed loop, or when the user asks how to verify / smoke-test.
---

# Local verify

## Trust ladder (low → high)

| Check | Catches | Limitation |
| --- | --- | --- |
| `mvn -pl <module> -am test` | Unit/Mockito | Not Spring Context / wiring |
| Service `@SpringBootTest` | Missing beans, bad scan | Needs test deps/config |
| Mapper SqlSessionFactory test | Duplicate statement ids | Per-launcher XML only |
| `./scripts/validate-microservices-stack.sh` | Compose/module shape | Not full business path |
| `./scripts/smoke-test-microservices.sh` | Basic health/routing | Not logged-in draw/MQ final state |
| `./scripts/smoke-api.sh` | HTTP reachability | Often prints only — weak assertions |
| `./scripts/validate-microservices-runtime-safety.sh` | Some static patterns | **Known false green** — never sole gate |
| Manual/DB check after Docker | Real closed loop | Heavier |
| `npm test` (Playwright) | UI flows | Many tests are visibility-only |

## Recommended by change type

- **Boot/scan/mapper/XXL:** Context tests + mapper parse + (if Docker) health on 8083/8085/8080.
- **Money/stock/MQ:** unit/integration for idempotency + one end-to-end DB/Redis/MQ state check.
- **Frontend stage/logout:** Playwright or manual assert activityId + revoked JWT.
- **Docs-only:** no stack required.

## Typical commands

```bash
mvn -pl big-market-market-service,big-market-message-job-service -am test
./scripts/validate-microservices-stack.sh
./scripts/smoke-test-microservices.sh
./scripts/smoke-api.sh
npm test
```

Full stack (when user wants):

```bash
docker compose -f docs/dev-ops/docker-compose-environment.yml up -d
docker compose up --build -d
```

## Reporting

When summarizing verification, state **what ran** and **what it cannot prove**. Do not say “closed loop verified” after only runtime-safety or compile.

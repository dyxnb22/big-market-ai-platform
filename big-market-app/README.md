# big-market-app (Legacy Monolith)

> **Status: legacy / non-primary.** The supported local path is the microservices
> stack in the repository root `docker-compose.yml` (gateway on port 8080).

## What this module is

`big-market-app` is the original single-JAR launcher that wires
`big-market-trigger` + `big-market-infrastructure` into one Spring Boot process.
It predates the split into `auth-service`, `market-service`, `admin-service`, and
the other deployable modules.

## Why it is still in the repo

1. **Integration tests** — most DAO/domain/trigger tests live under
   `src/test/java` and boot the full stack from this module.
2. **Historical reference** — useful when comparing monolith vs microservice
   wiring.
3. **Gradual retirement** — tests should be migrated to focused modules before
   this launcher is removed.

## What not to do

- Do **not** use this module for new features; add HTTP/RPC entry points to the
  appropriate microservice or shared `trigger` / `domain` libraries.
- Do **not** treat duplicate config here (`TokenAuthInterceptor`, MyBatis mapper
  copies, etc.) as the source of truth — microservice modules own runtime config.

## How to run (only if you explicitly need the monolith)

```bash
mvn clean package -pl big-market-app -am -DskipTests
java -jar big-market-app/target/big-market-app.jar --spring.profiles.active=dev
```

For normal development, prefer:

```bash
docker compose up --build -d
./scripts/smoke-api.sh
```

# ADR: Stack upgrade to Java 17 + Spring Boot 3.5

- Status: Accepted (PoC branch `upgrade/java17-boot3`)
- Date: 2026-07-18
- Completed: 2026-07-18 (Phase 0→7 on this branch)

## Context

Previous baseline was Java 8 + Spring Boot 2.7.12 + Spring Framework 5.3.x, with Spring Cloud Gateway versions pinned per-component (no Cloud BOM) and no Spring Cloud Alibaba. Several runtime dependencies (Boot 2.7, Redis 6.2, RabbitMQ 3.12) were at or past community maintenance boundaries.

`docs/LEARNING-FREEZE.md` previously treated Java 8 / Boot 2.7 as the learning freeze baseline. This ADR records the completed progressive upgrade on PoC branch `upgrade/java17-boot3`.

## Decision

| Item | Choice |
| --- | --- |
| Target | Java 17 + Spring Boot **3.5.16** + Spring Cloud **2025.0.3** |
| SCA | Do **not** introduce Spring Cloud Alibaba; keep direct Nacos Client + Dubbo |
| Path | Phase 0 baseline → Phase 1 BOM/deps → JDK 17 → Boot 2.7.18 → Boot 3.5/Jakarta → middleware one-by-one |
| Branch | `upgrade/java17-boot3` |
| Topology / money-path | Unchanged (seven services, outbox, idempotency keys, XXL appname `big-market-message-job`) |

## Final stack (this branch)

| Layer | Version |
| --- | --- |
| JDK | 17 (`maven.compiler.release=17`, Temurin 17 JRE images) |
| Spring Boot | 3.5.16 |
| Spring Cloud | 2025.0.3 |
| Dubbo | 3.3.6 |
| MyBatis Spring Boot | 3.0.5 |
| Redisson | 3.45.1 |
| Nacos client / server | 3.2.3 / `nacos/nacos-server:v3.2.3-slim` |
| XXL-Job core / admin | 2.5.0 / `kuschzzp/xxl-job-aarch64:2.5.0` (Apple Silicon) |
| Redis | 7.4.9 |
| RabbitMQ | 4.3.2 |
| MySQL server / driver | 8.4.5 / `mysql-connector-j` 8.4.0 |

## Operational notes

- Starters register via `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` only (legacy `spring.factories` removed).
- Temporary JDK 17 `--add-opens` for Dubbo Hessian remain in `Dockerfile.service` / Surefire `argLine` (narrow opens only).
- Nacos 3.x: Admin API auth disabled in learning compose (`NACOS_AUTH_ADMIN_ENABLE=false`). Do **not** enable `nacos.config.namespace.compatible.mode`. Config sync aligns live tenant to empty (SDK write target). Publish is fail-closed via JDBC confirmation against empty-tenant MySQL (`nacos.config.sync.confirmJdbcUrl` on admin) and mirrors the payload to the `public` twin that `getConfig` reads. Local snapshots must disable via reflection or admin refuses to start sync; snapshot clear failures also fail-closed. Redis topics fan out runtime/platform updates. Migrations keep empty as seed SoT and ensure a matching public twin. Learning Docker Dubbo providers disable metadata-report to avoid Nacos 3 metadata noise.
- Gateway uses Boot 3.5 / Cloud 2025 keys: `spring.cloud.gateway.server.webflux.*` and `management.prometheus.metrics.export.enabled`.
- Local scripts (`acceptance.sh`, `validate-microservices-stack.sh`, `dev-run.sh`) precheck JDK 17+.
- HTTP services pull `spring-boot-starter-validation` via `big-market-starter-web`.
- MySQL 8.4: `my.cnf` uses `mysql_native_password=ON` (removed `default-authentication-plugin`).
- Stock Lua scripts use Redisson `StringCodec` (JsonJacksonCodec broke `tonumber` ARGV on JDK 17 / newer Redisson).
- JUnit Vintage kept: several unit tests still use `org.junit.Test`.
- `validate-microservices-stack.sh --skip-build` mutates the learning DB; not read-only.
- Fresh-volume acceptance remains opt-in and destructive.

## Merge gate

Reuse acceptance + `smoke-raffle-award-e2e.sh` + chat refund E2E must be green on this working tree before merging to main.

## References

- Spring Boot 3.5 / Spring Cloud 2025.0 support matrix
- MyBatis Spring Boot Starter 3.0.x ↔ Boot 3.x
- Nacos 3.x upgrade / namespace migration docs

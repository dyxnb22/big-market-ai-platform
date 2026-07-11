# Java 17 / Spring Boot 3 Upgrade Checklist

Status: **not started / PoC branch only**

Baseline (current HEAD): Java **8** (`java.version=1.8`), Spring Boot **2.7.12**.  
This document is an inventory only — **do not change `java.version` in the root POM** until a dedicated PoC branch proves green builds and smoke.

## Scope

| Area | Current | Boot 3 / JDK 17 target notes | Status |
| --- | --- | --- | --- |
| JDK | 1.8 | 17 LTS (toolchain + CI image) | not started / PoC branch only |
| Spring Boot | 2.7.12 | 3.2+ / 3.3 LTS line | not started / PoC branch only |
| `javax.*` → `jakarta.*` | Widespread `javax.annotation`, `javax.servlet`, JAXB | Namespace + dependency swap | not started / PoC branch only |
| Dubbo | 3.0.9 | Need Boot 3–compatible Dubbo 3.2+ line; retest Nacos registry | not started / PoC branch only |
| Nacos client | 2.1.0 | Align with Dubbo/Spring Cloud Alibaba matrix for Boot 3 | not started / PoC branch only |
| MyBatis Spring Boot | 2.1.4 | `mybatis-spring-boot-starter` 3.x (Jakarta) | not started / PoC branch only |
| XXL-Job | 2.4.1 | Confirm executor + admin on JDK 17; reseed if API changes | not started / PoC branch only |
| Spring Security | Not used as a starter (custom JWT in auth/gateway) | If introduced later, use Security 6 / Jakarta; today only inventory JWT filters & CORS | not started / PoC branch only |
| AMQP starter | Declared `spring-boot-starter-amqp` **3.2.0** under Boot 2.7 parent | Resolve version skew before/during Boot 3 PoC | not started / PoC branch only |
| Redisson | 3.26.0 | Verify Boot 3 starter artifact | not started / PoC branch only |
| MySQL connector | `mysql-connector-java` 8.0.23 | Prefer `com.mysql:mysql-connector-j` | not started / PoC branch only |
| JAXB | `javax.xml.bind:jaxb-api` | Jakarta XML Binding on JDK 17 | not started / PoC branch only |

## javax → jakarta hotspots (inventory)

- Annotations: `javax.annotation.PostConstruct` / `Resource` (e.g. metrics, jobs).
- Servlet API via Spring MVC / gateway filters (transitive `javax.servlet`).
- Validation (`javax.validation` if present on DTOs).
- JAXB / XML binding for any remaining XML tooling.
- Do **not** bulk-replace on `main` until PoC compiles all modules.

## Suggested PoC order (when started)

1. Branch from green HEAD; keep Java 8 CI green on `main`.
2. Raise compiler/plugins and test on JDK 17 **without** Boot 3 (optional intermediate).
3. Boot 3 + Jakarta namespace on one leaf service (e.g. auth), then market + message-job.
4. Retest Dubbo/Nacos registration, MyBatis mappers, XXL executor `appname`, Rabbit consumers.
5. Regression: Context tests → `./scripts/acceptance.sh --reuse` → smoke/Playwright.

## Explicit non-goals (this checklist)

- Changing root `pom.xml` `java.version` / Boot parent on `main`.
- Physical DB-per-service or market service-split.
- Claiming upgrade “done” without PoC evidence.

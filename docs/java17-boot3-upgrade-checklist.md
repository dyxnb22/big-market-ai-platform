# Java 17 / Spring Boot 3 Upgrade Checklist

Status: **completed on branch `upgrade/java17-boot3` (2026-07-18)**

Final baseline: Java **17**, Spring Boot **3.5.16**, Spring Cloud **2025.0.3**.  
Authoritative decision record: `docs/adr/2026-07-18-stack-upgrade.md`.  
Readiness wording: `docs/LEARNING-FREEZE.md`.

## Outcome

| Area | Final |
| --- | --- |
| JDK | 17 (`maven.compiler.release=17`, Temurin 17 JRE) |
| Spring Boot / Cloud | 3.5.16 / 2025.0.3 |
| Jakarta EE packages | `javax.annotation\|servlet\|validation` → `jakarta.*` |
| Dubbo | 3.3.6 |
| Nacos client / server | 3.2.3 / v3.2.3-slim |
| MyBatis Spring Boot | 3.0.5 |
| XXL-Job | 2.5.0 |
| Redisson | 3.45.1 |
| Redis / Rabbit / MySQL | 7.4.9 / 4.3.2 / 8.4.5 |
| MySQL driver | `com.mysql:mysql-connector-j` 8.4.0 |
| Starter auto-config | `AutoConfiguration.imports` only |

## Merge / verify gate

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17   # or equivalent JDK 17
mvn -B clean verify -DfailIfNoTests=false
./scripts/smoke-raffle-award-e2e.sh
./scripts/smoke-chat-refund-e2e.sh
./scripts/acceptance.sh --reuse
```

Do not claim fresh/secure closed loop without running those modes explicitly.

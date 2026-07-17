# Unused / optional dependency inventory (GOV-B07)

Status as of 2026-07-17. These libraries appear in dependencyManagement or module POMs but are **not** required for the current default learning-freeze acceptance path (`acceptance.sh --reuse`).

| Capability | Artifact | Current use | Action |
| --- | --- | --- | --- |
| Hystrix | `com.netflix.hystrix:hystrix-javanica` | Declared in root DM; gateway uses Resilience4j | Keep DM entry for legacy; do not add new Hystrix usage. Prefer Resilience4j. |
| Canal | `canal-server` / `canal-adapter` in compose | Infra containers only; no app consumer in default path | Documented as optional CDC learning stack; not required for acceptance. |
| Elasticsearch | `x-pack-sql-jdbc` + ES/Kibana compose | Optional analytics JDBC URL in some yml | Not required for raffle/credit/chat acceptance. |

Do not remove these from compose without updating `docs/MICROSERVICES.md` and learning guides that reference them.

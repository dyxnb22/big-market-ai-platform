---
name: microservice-boot-fix
description: >-
  Fixes Big Market service startup failures from component-scan gaps, duplicate
  MyBatis statements, and XXL-Job appname/seed mismatch (BM-001/002/003). Use
  when market-service or message-job-service fails to start, Spring missing
  beans, StrictMap duplicate, gateway waits on market health, or XXL handlers
  never fire.
---

# Microservice boot fix

## Diagnose

1. Read `MarketServiceApplication` / `MessageJobServiceApplication` `scanBasePackages`.
2. Trace `@Autowired` from Controllers/Listeners/Jobs → `trigger.application` / `adapter`.
3. Grep mapper XML for duplicate `id="..."` within the same file.
4. Compare `xxl.job.executor.appname` in message-job `application.yml` vs `docs/dev-ops/mysql/sql/xxl_job.sql` `xxl_job_group.app_name`.
5. List `@XxlJob` handlers vs `xxl_job_info.executor_handler` + `trigger_status`.

## Fix order (do not reorder)

### BM-001 market

- Add scan (or `@Import`) for `trigger.application`, `trigger.support`, `trigger.adapter`.
- Keep **out** `trigger.job` / `trigger.listener`.
- Keep the final local adapters under `com.dyx.market.market.config`; only account credit/quota remote-write paths remain intentionally remote.

### BM-002 message-job

- Remove duplicate mapper statements; align with other services’ XML if needed.
- Ensure consumers/jobs can inject application services (`trigger.application` scan or import).
- Keep **out** `trigger.http` / `trigger.rpc`.

### BM-003 XXL

- Unify appname and SQL group (one name everywhere; document the choice).
- Seed all required handlers; enable (`trigger_status=1`) those needed for demo.
- Optional gate: reflect `@XxlJob` names and diff against SQL.

## Required tests

- `@SpringBootTest` for market and message-job (context loads).
- Mapper `SqlSessionFactory` build for message-job (and any edited launcher).
- Do not claim fixed based only on `mvn compile` or `runtime-safety` script.
- Do not re-add retired standalone launchers, RPC contracts, or Provider-mode flags to solve a boot issue.

## Related

- Rule: `.cursor/rules/service-boot-scan.mdc`, `mapper-and-xxl.mdc`

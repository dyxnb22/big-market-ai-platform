# Phase 0 baseline — stack upgrade PoC

- Date: 2026-07-18
- Branch: `upgrade/java17-boot3`
- Host JDK (default): Amazon Corretto 8.0.462
- Available upgrade JDK: Homebrew OpenJDK 17.0.18 (`/opt/homebrew/opt/openjdk@17`)
- Maven: 3.9.9

## Verified

| Gate | Result | Notes |
| --- | --- | --- |
| `mvn -B clean verify -DfailIfNoTests=false` | PASS | 19/19 modules; includes new account Context test |
| `validate-microservices-runtime-safety.sh` | PASS | 99/99 |
| `validate-mapper-ddl-gates.sh` | PASS | 23/23; mapper contracts compared=141 exceptions=8 errors=0 |
| `docker compose config --quiet` | PASS | |
| Seven app containers healthy | PASS | Running ~16–19h at baseline time |
| Core middleware (mysql/redis/rabbitmq/nacos/es) | PASS | Healthy |
| `smoke-test-microservices.sh` | PASS | 20/20 |
| Account `@SpringBootTest` Context | PASS | Added in Phase 0 |

## Acceptance (`./scripts/acceptance.sh --reuse`)

First run **FAILED** at `xxl-job-admin health` (admin container was not running; timed out 120s). Gates before that PASS: mvn verify, migrations, stack health, demo activity, HTTP contracts, smoke microservices, smoke-api, nacos runtime config.

XXL Admin was started afterward (`docker compose -f docs/dev-ops/docker-compose-environment.yml up -d xxl-job-admin`). Re-run recorded below after Phase 0 close-out.

## Explicitly not claimed

- `--fresh` empty volumes (destructive; needs separate authorization)
- `--secure` overlay dynamic acceptance
- Full CVE/SBOM audit

## Notes

- `validate-microservices-stack.sh --skip-build` applies migrations/seeds and clears strategy cache — **not read-only**.
- ADR: `docs/adr/2026-07-18-stack-upgrade.md`

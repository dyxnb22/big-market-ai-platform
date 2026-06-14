# Archived Evidence Template

Status: historical archive. This template is retained for readers who want to
see which readiness topics were considered. It is not a current gate.

## Local Learning Evidence

| Topic | Local evidence |
| --- | --- |
| DDL | `docs/sql/*.sql`, `docs/dev-ops/mysql/sql/*.sql` |
| Service registration | service `application.yml`, Dubbo provider classes, Nacos config |
| MQ | RabbitMQ listeners, DLQ config, dev compose |
| XXL-Job | job handlers and `XxlJobConfig` |
| Monitoring | actuator, Prometheus config, Grafana config, trace filters |
| Acceptance | Maven build, smoke scripts, final-architecture guardrail script |
| Rollback | `docs/operations-checklist.md`, `docs/data-and-outbox.md` |
| Old path cleanup | `docs/old-path-cleanup-inventory.md` |

## Current Standard

For this learning project, completion is demonstrated by local validation and
code/documentation consistency. Real production evidence collection is outside
the project scope.

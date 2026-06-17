# 10 Problems, Fixes, And Troubleshooting

## Current Learning-Scope Fixes

The repository has been normalized as a final-state microservices learning
project:

- Architecture docs now describe the completed local service architecture.
- Learning notes use one reading order: architecture, request flow, domain
  model, service boundary, data/tasks, MQ/XXL-Job, idempotency, rollback,
  monitoring, and code map.
- SQL notes describe learning-environment DDL references.
- Script output describes final architecture validation with current script
  names.

## Common Troubleshooting Paths

- Build failure: run `mvn clean package -DskipTests` from the repository root
  and inspect the first module that fails.
- Gateway route failure: check
  `big-market-gateway/src/main/resources/application.yml` and
  `big-market-gateway/src/main/java/com/dyx/market/gateway/fallback/FallbackController.java`.
- Login failure: check
  `big-market-auth-service/src/main/resources/application.yml` and
  `big-market-auth-service/src/main/java/com/dyx/market/auth/AuthAccessController.java`.
- Raffle failure: trace `RaffleActivityController.draw_by_token`,
  `RaffleApplicationService.executeDraw`, `ActivityRepository`, and
  `AwardRepository`.
- MQ retry failure: check `SendMessageTaskJob`, `RabbitMQDlqConfig`, and the
  relevant listener in `big-market-trigger/src/main/java/com/dyx/market/trigger/listener`.
- Job scheduling failure: check
  `big-market-message-job-service/src/main/java/com/dyx/market/message/job/config/XxlJobConfig.java`.

## Remaining Learning Risks

This is not a real production environment. Local smoke tests and validators
replace production observation windows. External providers such as OpenAI quota
adjustment still depend on environment-specific credentials and network access.

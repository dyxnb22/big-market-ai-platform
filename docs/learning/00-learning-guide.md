# 00 Learning Guide

## Purpose

Big Market is a completed local microservices learning project for a marketing
raffle platform. It demonstrates login, gateway routing, activity quota,
credit, raffle strategy, award fulfillment, rebate, MQ compensation, XXL-Job
tasks, and local observability.

Start with these files:

- `docs/MICROSERVICES.md`
- `docker-compose.yml`
- `docs/dev-ops/docker-compose-environment.yml`
- `big-market-gateway/src/main/resources/application.yml`
- `big-market-trigger/src/main/java/com/dyx/market/trigger/http/RaffleActivityController.java`
- `big-market-domain/src/main/java/com/dyx/market/domain/activity/application/RaffleApplicationService.java`
- `big-market-message-job-service/src/main/java/com/dyx/market/message/job/config/DispatchCreditAwardTaskJob.java`

## Ten-Step Learning Path

1. Overall architecture: read `docs/MICROSERVICES.md` and
   `docs/learning/03-architecture-overview.md`.
2. Request flow: trace gateway routes into auth, market, admin, and chatbot.
3. Domain model: read activity, strategy, award, credit, rebate, auth, and task
   packages under `big-market-domain/src/main/java/com/dyx/market/domain`.
4. Service boundaries: compare service launchers and shared libraries in
   `pom.xml`.
5. Data and tasks: read `docs/data-and-outbox.md` and MyBatis mappers under
   each service resource directory.
6. MQ/XXL-Job: read RabbitMQ listeners under `big-market-trigger` and job
   handlers under `big-market-message-job-service`.
7. Idempotency and consistency: inspect unique business keys in SQL and
   repository methods.
8. Degradation and rollback: inspect gateway fallback, quota rollback, credit
   refund, task retry, and DLQ logging.
9. Monitoring and troubleshooting: read `docs/operations-checklist.md`.
10. Code map: use `docs/learning/09-code-map.md` as the jump table.

## Local Completion Standard

The learning environment is complete when the code builds, the local smoke
script passes, final-architecture guardrails pass, and the documentation matches
the code paths above. Real production observation periods are outside this
portfolio project.

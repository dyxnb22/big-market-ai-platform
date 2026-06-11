package com.dyx.market.fulfillment;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * Fulfillment service: Dubbo RPC provider for award fulfillment (prize dispatch after raffle win).
 *
 * Phase 2.3-A dark launch: service registers Dubbo provider but receives no traffic yet.
 * Existing callers in message-job-service (SendAwardConsumer) still call IAwardService
 * in-process. Traffic cutover happens in Phase 2.3-B+ after the credit-award outbox is
 * staging-validated.
 *
 * Scans:
 *   - own config (com.dyx.market.fulfillment)
 *   - domain.award — award domain services (AwardService, UserCreditRandomAward, etc.)
 *   - infrastructure — DAOs, repositories, Redis, EventPublisher
 *
 * Does NOT scan:
 *   - trigger.job    — XXL-Job handlers (owned by message-job-service)
 *   - trigger.listener — MQ consumers (owned by message-job-service; SendAwardConsumer stays there)
 *   - trigger.http   — HTTP controllers (owned by market-service)
 *   - trigger.rpc    — Existing Dubbo providers (owned by market-service)
 */
@SpringBootApplication(scanBasePackages = {
        "com.dyx.market.fulfillment",
        "com.dyx.market.domain.award",
        "com.dyx.market.domain.activity.adapter.event",
        "com.dyx.market.infrastructure"
})
@EnableDubbo
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class FulfillmentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(FulfillmentServiceApplication.class, args);
    }

}

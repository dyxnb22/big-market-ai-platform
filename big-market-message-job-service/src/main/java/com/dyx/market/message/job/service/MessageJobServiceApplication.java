package com.dyx.market.message.job.service;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Message-job service: owns all MQ consumers and XXL-Job scheduled handlers.
 * Extracted from big-market-market-service in Phase 2.1.
 *
 * Scans only the packages required for job and listener execution:
 *   - own config (this package)
 *   - trigger.job  — XXL-Job handlers
 *   - trigger.listener — RabbitMQ consumers
 *   - domain  — domain services (called by consumers/jobs)
 *   - infrastructure — DAOs, repositories, Redis, EventPublisher
 *
 * Does NOT scan:
 *   - trigger.http  — HTTP controllers (stay in market-service)
 *   - trigger.rpc   — Dubbo RPC provider (stays in market-service)
 */
@SpringBootApplication(scanBasePackages = {
        "com.dyx.market.message.job",
        "com.dyx.market.trigger.job",
        "com.dyx.market.trigger.listener",
        "com.dyx.market.domain",
        "com.dyx.market.infrastructure"
})
@EnableDubbo
@EnableScheduling
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class MessageJobServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MessageJobServiceApplication.class, args);
    }

}

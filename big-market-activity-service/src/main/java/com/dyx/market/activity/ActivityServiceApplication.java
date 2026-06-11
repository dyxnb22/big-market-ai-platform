package com.dyx.market.activity;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.ImportResource;

/**
 * Activity service: Phase 5-F dark-launch scaffold.
 *
 * This launcher establishes the activity bounded context as a standalone Spring Boot
 * process. In this batch it is a structural boundary only — no Dubbo provider, no
 * HTTP controller, no MQ consumer, and no XXL-Job handler are registered.
 *
 * Draw execution (RaffleApplicationService), the HTTP ingress
 * (RaffleActivityController), and the three-step orchestration
 * (createOrder → performRaffle → saveUserAwardRecord) remain in
 * big-market-market-service until Phase 5-G saga/outbox design is approved
 * and Phase 7 activity table ownership is resolved.
 *
 * Scan boundary:
 *   - com.dyx.market.activity      (this module only)
 *   - com.dyx.market.infrastructure (shared infra beans required for DB/Redis/MQ wiring)
 *
 * Explicitly excluded from scan:
 *   - com.dyx.market.trigger.*     (HTTP controllers, job handlers, listeners)
 *   - com.dyx.market.domain.strategy
 *   - com.dyx.market.domain.award
 *   - com.dyx.market.domain.rebate
 */
@SpringBootApplication(scanBasePackages = {
        "com.dyx.market.activity",
        "com.dyx.market.infrastructure"
})
@EnableDubbo
@ImportResource(locations = {"classpath:spring-config.xml"})
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class ActivityServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ActivityServiceApplication.class, args);
    }

}

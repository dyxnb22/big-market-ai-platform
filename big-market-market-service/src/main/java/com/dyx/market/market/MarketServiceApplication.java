package com.dyx.market.market;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.ImportResource;

/**
 * Core market service: HTTP APIs, Dubbo RPC provider for rebate service.
 * MQ consumers and XXL-Job handlers have moved to big-market-message-job-service.
 *
 * Scans only what is needed for HTTP + Dubbo:
 *   - own config (com.dyx.market.market)
 *   - trigger.http  — REST controllers
 *   - trigger.rpc   — Dubbo RPC provider (RebateServiceRPC)
 *   - domain        — domain services
 *   - infrastructure — DAOs, repositories, Redis, EventPublisher
 *
 * Does NOT scan trigger.job or trigger.listener — those are owned by message-job-service.
 */
@SpringBootApplication(scanBasePackages = {
        "com.dyx.market.market",
        "com.dyx.market.trigger.http",
        "com.dyx.market.trigger.rpc",
        "com.dyx.market.domain",
        "com.dyx.market.infrastructure"
})
@EnableDubbo
@ImportResource(locations = {"classpath:spring-config.xml"})
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class MarketServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MarketServiceApplication.class, args);
    }

}

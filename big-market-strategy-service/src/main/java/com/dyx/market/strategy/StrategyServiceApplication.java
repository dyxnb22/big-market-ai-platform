package com.dyx.market.strategy;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.ImportResource;

/**
 * Strategy service: dark-launch Dubbo provider for read-only strategy queries.
 *
 * Phase 4-C scaffold only: the existing market-service provider remains active.
 * Draw execution (performRaffle), stock mutation, and armory assembly remain in
 * market-service until Phase 5. This launcher exposes only IStrategyReadService.
 *
 * Scan boundary: strategy module + strategy domain + infrastructure only.
 * No HTTP controllers, MQ listeners, or job handlers are included.
 * Draw execution, activity orchestration, rebate, credit, and auth domains
 * are outside the scan scope of this launcher.
 */
@SpringBootApplication(scanBasePackages = {
        "com.dyx.market.strategy",
        "com.dyx.market.domain.strategy",
        "com.dyx.market.infrastructure"
})
@EnableDubbo
@ImportResource(locations = {"classpath:spring-config.xml"})
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class StrategyServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(StrategyServiceApplication.class, args);
    }

}

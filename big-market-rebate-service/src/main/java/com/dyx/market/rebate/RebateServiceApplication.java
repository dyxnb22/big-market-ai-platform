package com.dyx.market.rebate;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.ImportResource;

/**
 * Rebate service: dark-launch Dubbo provider for behavior rebate creation.
 *
 * Phase 3 scaffold only: the existing market-service provider remains active.
 * This launcher gives the rebate bounded context its own service boundary before
 * any caller or traffic cutover is attempted.
 */
@SpringBootApplication(scanBasePackages = {
        "com.dyx.market.rebate",
        "com.dyx.market.domain.rebate",
        "com.dyx.market.infrastructure"
})
@EnableDubbo
@ImportResource(locations = {"classpath:spring-config.xml"})
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class RebateServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(RebateServiceApplication.class, args);
    }

}

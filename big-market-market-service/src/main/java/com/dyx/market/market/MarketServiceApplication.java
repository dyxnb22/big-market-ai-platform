package com.dyx.market.market;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.ImportResource;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Core market service: raffle, activity, strategy, awards, rebate, credit.
 * Equivalent to the original big-market-app minus auth/admin/chatbot controllers.
 */
@SpringBootApplication(scanBasePackages = "com.dyx.market")
@EnableScheduling
@EnableDubbo
@ImportResource(locations = {"classpath:spring-config.xml"})
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class MarketServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MarketServiceApplication.class, args);
    }

}

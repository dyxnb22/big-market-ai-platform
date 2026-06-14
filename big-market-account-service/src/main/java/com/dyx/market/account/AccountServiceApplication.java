package com.dyx.market.account;

import com.dyx.market.account.config.LocalActivityPortConfig;
import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;

/**
 * Account service: Dubbo RPC provider for credit and activity quota operations.
 *
 * Service registers Dubbo providers for credit and quota operations. Local development can
 * still use in-process adapters for compact learning runs.
 * Scans:
 *   - own config (com.dyx.market.account)
 *   - domain — credit + activity quota domain services (and all others; shared JAR)
 *   - infrastructure — DAOs, repositories, Redis, EventPublisher
 *
 * Does NOT scan:
 *   - trigger.http   — HTTP controllers (owned by market-service)
 *   - trigger.rpc    — Existing Dubbo providers (owned by market-service)
 *   - trigger.job    — XXL-Job handlers (owned by message-job-service)
 *   - trigger.listener — MQ consumers (owned by message-job-service)
 */
@SpringBootApplication(scanBasePackages = {
        "com.dyx.market.account",
        "com.dyx.market.domain",
        "com.dyx.market.infrastructure"
})
@EnableDubbo
@Import(LocalActivityPortConfig.class)
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class AccountServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AccountServiceApplication.class, args);
    }

}

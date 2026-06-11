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
 * Phase 2.2-A dark launch: service registers Dubbo providers but receives no
 * traffic yet. Existing callers in market-service and message-job-service still
 * call domain services in-process. Traffic cutover happens in Phase 2.2-B.
 *
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

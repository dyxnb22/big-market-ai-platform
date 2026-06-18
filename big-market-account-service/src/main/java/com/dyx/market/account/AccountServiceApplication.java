package com.dyx.market.account;

import com.dyx.market.account.config.LocalActivityPortConfig;
import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;

/**
 * 账户服务启动入口：对外提供积分与活动额度相关的 Dubbo RPC。
 * <p>
 * 注册积分（credit）与活动额度（quota）的 Dubbo Provider；本地开发可通过
 * {@link LocalActivityPortConfig} 使用进程内适配器，便于精简学习运行。
 * <p>
 * 组件扫描范围：
 * <ul>
 *   <li>{@code com.dyx.market.account} — 本服务配置与 Provider</li>
 *   <li>{@code com.dyx.market.domain} — 领域服务（积分、活动额度等，共享 JAR）</li>
 *   <li>{@code com.dyx.market.infrastructure} — DAO、仓储、Redis、EventPublisher</li>
 * </ul>
 * 不扫描：
 * <ul>
 *   <li>{@code trigger.http} — HTTP 控制器（归属 market-service）</li>
 *   <li>{@code trigger.rpc} — 已有 Dubbo Provider（归属 market-service）</li>
 *   <li>{@code trigger.job} — XXL-Job 处理器（归属 message-job-service）</li>
 *   <li>{@code trigger.listener} — MQ 消费者（归属 message-job-service）</li>
 * </ul>
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

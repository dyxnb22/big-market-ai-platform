package com.dyx.market.account;

import com.dyx.market.account.config.LocalActivityPortConfig;
import com.dyx.market.domain.activity.application.RaffleApplicationService;
import com.dyx.market.domain.activity.service.partake.RaffleActivityPartakeService;
import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.ImportResource;
import org.springframework.context.annotation.FilterType;

/**
 * 账户服务启动入口：对外提供积分与活动额度相关的 Dubbo RPC。
 * <p>
 * 注册积分（credit）与活动额度（quota）的 Dubbo Provider；本地开发可通过
 * {@link LocalActivityPortConfig} 使用进程内适配器，便于精简学习运行。
 * <p>
 * 组件扫描范围：
 * <ul>
 *   <li>{@code com.dyx.market.account} — 本服务配置与 Provider</li>
 *   <li>{@code com.dyx.market.domain} — 领域服务（积分、活动额度等，共享 JAR；排除仅用于 market
 *       抽奖入口的编排服务）</li>
 *   <li>{@code com.dyx.market.infrastructure} — DAO、仓储、Redis、EventPublisher</li>
 *   <li>{@code com.dyx.market.trigger.account} — docker/secure 下账户余额校验所需的 Dubbo 端口</li>
 * </ul>
 * 不扫描：
 * <ul>
 *   <li>{@code trigger.http} — HTTP 控制器（归属 market-service）</li>
 *   <li>{@code trigger.rpc} — 已有 Dubbo Provider（归属 market-service）</li>
 *   <li>{@code trigger.job} — XXL-Job 处理器（归属 message-job-service）</li>
 *   <li>{@code trigger.listener} — MQ 消费者（归属 message-job-service）</li>
 * </ul>
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan(basePackages = {
        "com.dyx.market.account",
        "com.dyx.market.domain",
        "com.dyx.market.infrastructure",
        "com.dyx.market.trigger.account"
}, excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {
        RaffleApplicationService.class,
        RaffleActivityPartakeService.class
}))
@EnableDubbo
@Import(LocalActivityPortConfig.class)
@ImportResource(locations = {"classpath:spring/spring-config-token.xml"})
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class AccountServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AccountServiceApplication.class, args);
    }

}

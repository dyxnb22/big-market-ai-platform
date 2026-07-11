package com.dyx.market.market;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.ImportResource;

/**
 * 核心市场服务：对外提供 HTTP API，并作为返利等能力的 Dubbo RPC 提供方。
 * <p>
 * MQ 消费者与 XXL-Job 任务已迁移至 {@code big-market-message-job-service}。
 * <p>
 * 仅扫描 HTTP + Dubbo 所需组件：
 * <ul>
 *   <li>{@code com.dyx.market.market} — 本服务配置</li>
 *   <li>{@code trigger.http} — REST 控制器</li>
 *   <li>{@code trigger.rpc} — Dubbo RPC 提供方（如 RebateServiceRPC）</li>
 *   <li>{@code trigger.application} — 应用编排服务（Facade / ApplicationService）</li>
 *   <li>{@code trigger.support} — HTTP 鉴权等支撑组件</li>
 *   <li>{@code trigger.adapter} — 本地写适配器（remote 关闭时生效）</li>
 *   <li>{@code domain} — 领域服务</li>
 *   <li>{@code infrastructure} — DAO、仓储、Redis、事件发布</li>
 * </ul>
 * 不扫描 {@code trigger.job}、{@code trigger.listener}，由 message-job-service 负责。
 */
@SpringBootApplication(scanBasePackages = {
        "com.dyx.market.market",
        "com.dyx.market.trigger.http",
        "com.dyx.market.trigger.rpc",
        "com.dyx.market.trigger.application",
        "com.dyx.market.trigger.support",
        "com.dyx.market.trigger.adapter",
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

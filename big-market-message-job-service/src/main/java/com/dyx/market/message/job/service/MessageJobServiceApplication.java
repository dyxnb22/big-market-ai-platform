package com.dyx.market.message.job.service;

import com.dyx.market.message.job.config.LocalActivityPortConfig;
import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.ImportResource;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 消息与定时任务服务启动入口：托管全部 MQ 消费者与 XXL-Job 定时处理器。
 * <p>
 * 从 big-market-market-service 拆分而来。
 * <p>
 * 组件扫描范围：
 * <ul>
 *   <li>{@code com.dyx.market.message.job} — 本服务配置</li>
 *   <li>{@code com.dyx.market.trigger.job} — XXL-Job 处理器</li>
 *   <li>{@code com.dyx.market.trigger.listener} — RabbitMQ 消费者</li>
 *   <li>{@code com.dyx.market.trigger.application} — 消费者/Job 依赖的应用服务</li>
 *   <li>{@code com.dyx.market.domain} — 领域服务（供消费者/Job 调用）</li>
 *   <li>{@code com.dyx.market.infrastructure} — DAO、仓储、Redis、EventPublisher</li>
 * </ul>
 * 不扫描：
 * <ul>
 *   <li>{@code trigger.http} — HTTP 控制器（保留在 market-service）</li>
 *   <li>{@code trigger.rpc} — Dubbo RPC Provider（保留在 market-service）</li>
 * </ul>
 */
@SpringBootApplication(scanBasePackages = {
        "com.dyx.market.message.job",
        "com.dyx.market.trigger.job",
        "com.dyx.market.trigger.listener",
        "com.dyx.market.trigger.application",
        "com.dyx.market.domain",
        "com.dyx.market.infrastructure"
})
@EnableDubbo
@Import(LocalActivityPortConfig.class)
@ImportResource(locations = {"classpath:spring/spring-config-token.xml"})
@EnableScheduling
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class MessageJobServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MessageJobServiceApplication.class, args);
    }

}

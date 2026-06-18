package com.dyx.market.fulfillment;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * 履约服务启动入口：对外提供抽奖中奖后的奖品发放（award fulfillment）Dubbo RPC。
 * <p>
 * 注册发奖履约 Dubbo Provider；本地开发仍可使用进程内发奖服务，便于精简学习运行。
 * <p>
 * 组件扫描范围：
 * <ul>
 *   <li>{@code com.dyx.market.fulfillment} — 本服务配置与 Provider</li>
 *   <li>{@code com.dyx.market.domain.award} — 发奖领域服务（AwardService、UserCreditRandomAward 等）</li>
 *   <li>{@code com.dyx.market.infrastructure} — DAO、仓储、Redis、EventPublisher</li>
 * </ul>
 * 不扫描：
 * <ul>
 *   <li>{@code trigger.job} — XXL-Job 处理器（归属 message-job-service）</li>
 *   <li>{@code trigger.listener} — MQ 消费者（归属 message-job-service；SendAwardConsumer 保留在该服务）</li>
 *   <li>{@code trigger.http} — HTTP 控制器（归属 market-service）</li>
 *   <li>{@code trigger.rpc} — 已有 Dubbo Provider（归属 market-service）</li>
 * </ul>
 */
@SpringBootApplication(scanBasePackages = {
        "com.dyx.market.fulfillment",
        "com.dyx.market.domain.award",
        "com.dyx.market.domain.activity.adapter.event",
        "com.dyx.market.infrastructure"
})
@EnableDubbo
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class FulfillmentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(FulfillmentServiceApplication.class, args);
    }

}

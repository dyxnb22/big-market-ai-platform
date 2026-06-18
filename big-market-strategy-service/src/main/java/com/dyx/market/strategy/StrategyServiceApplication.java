package com.dyx.market.strategy;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.ImportResource;

/**
 * 策略服务启动入口：对外提供只读策略查询的 Dubbo RPC。
 * <p>
 * 默认实现：本地学习运行时 market-service Provider 仍保持活跃。
 * 抽奖执行（performRaffle）、库存变更、军械库组装仍留在 market-service。
 * 本启动器仅暴露 {@code IStrategyReadService}。
 * <p>
 * 扫描边界：strategy 模块 + strategy 领域 + infrastructure，不含 HTTP 控制器、
 * MQ 监听器或 Job 处理器；抽奖执行、活动编排、返利、积分、认证等领域不在扫描范围内。
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

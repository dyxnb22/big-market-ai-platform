package com.dyx.market.rebate;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.ImportResource;

/**
 * 返利服务启动入口：对外提供行为返利创建的 Dubbo RPC。
 * <p>
 * 默认实现：现有 market-service Provider 仍保持活跃；本启动器为返利限界上下文
 * 提供独立服务边界，便于后续调用方或路由切换。
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

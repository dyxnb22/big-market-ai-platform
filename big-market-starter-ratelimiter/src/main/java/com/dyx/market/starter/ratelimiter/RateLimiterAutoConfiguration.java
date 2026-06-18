package com.dyx.market.starter.ratelimiter;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * 接口限流切面自动配置。
 */
@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class RateLimiterAutoConfiguration {

    /** 注册限流 AOP 切面 Bean。 */
    @Bean
    public RateLimiterAspect rateLimiterAspect() {
        return new RateLimiterAspect();
    }
}

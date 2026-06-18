package com.dyx.market.domain.auth.config;

import com.dyx.market.domain.auth.service.ITokenRevocationService;
import com.dyx.market.domain.auth.service.InMemoryTokenRevocationService;
import com.dyx.market.domain.auth.service.RedisTokenRevocationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * 共享 Token 吊销 Bean 配置，供扫描 {@code com.dyx.market.domain.auth} 的各服务使用。
 * <p>
 * 默认内存实现（单实例/本地开发）；设置 {@code token-revocation.redis.enabled=true}
 * 并提供 RedissonClient Bean 时切换为 Redis 实现。
 */
@Slf4j
@Configuration
public class TokenRevocationConfig {

    @Value("${token-revocation.redis.enabled:false}")
    private boolean redisEnabled;

    private final ApplicationContext applicationContext;

    public TokenRevocationConfig(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Bean
    @Primary
    public ITokenRevocationService tokenRevocationService() {
        if (redisEnabled) {
            try {
                Class<?> redissonClass = Class.forName("org.redisson.api.RedissonClient");
                Object redissonClient = applicationContext.getBean(redissonClass);
                log.info("[TokenRevocationConfig] using RedisTokenRevocationService");
                return new RedisTokenRevocationService(redissonClient);
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException("token-revocation.redis.enabled=true but RedissonClient is not on classpath", e);
            } catch (Exception e) {
                throw new IllegalStateException("token-revocation.redis.enabled=true but no RedissonClient bean is available", e);
            }
        }
        log.info("[TokenRevocationConfig] using InMemoryTokenRevocationService");
        return new InMemoryTokenRevocationService();
    }
}

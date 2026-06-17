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
 * Shared token-revocation bean for every service that scans {@code com.dyx.market.domain.auth}.
 *
 * <ul>
 *   <li>Default: in-memory (single instance / local dev)</li>
 *   <li>Redis: set {@code token-revocation.redis.enabled=true} and provide a RedissonClient bean</li>
 * </ul>
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

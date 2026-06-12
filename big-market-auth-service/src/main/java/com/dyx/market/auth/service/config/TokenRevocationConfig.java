package com.dyx.market.auth.service.config;

import com.dyx.market.domain.auth.service.ITokenRevocationService;
import com.dyx.market.domain.auth.service.InMemoryTokenRevocationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Provides the active ITokenRevocationService bean:
 * - InMemoryTokenRevocationService as the safe default
 * - RedisTokenRevocationService when token-revocation.redis.enabled=true
 *   AND RedissonClient is on the classpath (created via reflection at runtime)
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
                log.warn("[TokenRevocationConfig] token-revocation.redis.enabled=true but "
                        + "RedissonClient is not on classpath. Using in-memory fallback.");
            } catch (Exception e) {
                log.warn("[TokenRevocationConfig] token-revocation.redis.enabled=true but "
                        + "no RedissonClient bean is available. Using in-memory fallback.", e);
            }
        }
        log.info("[TokenRevocationConfig] using InMemoryTokenRevocationService");
        return new InMemoryTokenRevocationService();
    }
}

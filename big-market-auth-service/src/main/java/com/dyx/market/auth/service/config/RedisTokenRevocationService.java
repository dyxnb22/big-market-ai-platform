package com.dyx.market.auth.service.config;

import com.dyx.market.domain.auth.service.ITokenRevocationService;
import lombok.extern.slf4j.Slf4j;

/**
 * Redis-backed token revocation.
 *
 * ACTIVATION: Requires a RedissonClient bean on the classpath AND
 * token-revocation.redis.enabled=true. Until then, the default
 * InMemoryTokenRevocationService handles revocation.
 *
 * To activate: add Redisson dependency to auth-service and set
 * TOKEN_REVOCATION_REDIS_ENABLED=true.
 *
 * Implementation note: this class deliberately avoids compile-time
 * dependency on Redisson. At runtime the bean factory uses reflection
 * to construct it when RedissonClient is available.
 */
@Slf4j
public class RedisTokenRevocationService implements ITokenRevocationService {

    private static final String REVOKED_KEY_PREFIX = "jwt:revoked:";

    private final Object redissonClient; // org.redisson.api.RedissonClient at runtime

    public RedisTokenRevocationService(Object redissonClient) {
        this.redissonClient = redissonClient;
        log.info("[RedisTokenRevocationService] active — using Redis for token revocation");
    }

    @Override
    public void revoke(String jti, long expiresAtMillis) {
        long ttlSeconds = Math.max(1, (expiresAtMillis - System.currentTimeMillis()) / 1000);
        String key = REVOKED_KEY_PREFIX + jti;
        try {
            // Reflection: redissonClient.getBucket(key).set("revoked", ttlSeconds, TimeUnit.SECONDS)
            Object bucket = redissonClient.getClass().getMethod("getBucket", String.class)
                    .invoke(redissonClient, key);
            bucket.getClass().getMethod("set", Object.class, long.class, java.util.concurrent.TimeUnit.class)
                    .invoke(bucket, "revoked", ttlSeconds, java.util.concurrent.TimeUnit.SECONDS);
            log.info("[RedisTokenRevocationService] revoked jti:{} ttl:{}s", jti, ttlSeconds);
        } catch (Exception e) {
            log.error("[RedisTokenRevocationService] failed to revoke jti:{}", jti, e);
        }
    }

    @Override
    public boolean isRevoked(String jti) {
        try {
            Object bucket = redissonClient.getClass().getMethod("getBucket", String.class)
                    .invoke(redissonClient, REVOKED_KEY_PREFIX + jti);
            return (boolean) bucket.getClass().getMethod("isExists").invoke(bucket);
        } catch (Exception e) {
            log.error("[RedisTokenRevocationService] failed to check jti:{}", jti, e);
            return false;
        }
    }

    @Override
    public long size() {
        return -1; // Expensive in Redis; use prometheus metrics instead
    }
}

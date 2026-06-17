package com.dyx.market.domain.auth.service;

import lombok.extern.slf4j.Slf4j;

/**
 * Redis-backed token revocation shared across auth, market, and admin services.
 *
 * Uses reflection so domain does not require a compile-time Redisson dependency.
 * A {@code org.redisson.api.RedissonClient} bean must be present when
 * {@code token-revocation.redis.enabled=true}.
 */
@Slf4j
public class RedisTokenRevocationService implements ITokenRevocationService {

    private static final String REVOKED_KEY_PREFIX = "jwt:revoked:";

    private final Object redissonClient;

    public RedisTokenRevocationService(Object redissonClient) {
        this.redissonClient = redissonClient;
        log.info("[RedisTokenRevocationService] active - using Redis for token revocation");
    }

    @Override
    public void revoke(String jti, long expiresAtMillis) {
        long ttlSeconds = Math.max(1, (expiresAtMillis - System.currentTimeMillis()) / 1000);
        String key = REVOKED_KEY_PREFIX + jti;
        try {
            Object bucket = redissonClient.getClass().getMethod("getBucket", String.class)
                    .invoke(redissonClient, key);
            bucket.getClass().getMethod("set", Object.class, long.class, java.util.concurrent.TimeUnit.class)
                    .invoke(bucket, "revoked", ttlSeconds, java.util.concurrent.TimeUnit.SECONDS);
            log.info("[RedisTokenRevocationService] revoked jti:{} ttl:{}s", jti, ttlSeconds);
        } catch (Exception e) {
            log.error("[RedisTokenRevocationService] failed to revoke jti:{}", jti, e);
            throw new IllegalStateException("Failed to revoke token in Redis", e);
        }
    }

    @Override
    public boolean isRevoked(String jti) {
        try {
            Object bucket = redissonClient.getClass().getMethod("getBucket", String.class)
                    .invoke(redissonClient, REVOKED_KEY_PREFIX + jti);
            return (boolean) bucket.getClass().getMethod("isExists").invoke(bucket);
        } catch (Exception e) {
            // Fail closed: treat Redis errors as revoked so logout/blacklist cannot be bypassed.
            log.error("[RedisTokenRevocationService] failed to check jti:{} - denying token", jti, e);
            return true;
        }
    }

    @Override
    public long size() {
        return -1;
    }
}

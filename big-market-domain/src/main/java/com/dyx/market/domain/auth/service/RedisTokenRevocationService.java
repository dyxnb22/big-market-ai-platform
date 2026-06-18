package com.dyx.market.domain.auth.service;

import lombok.extern.slf4j.Slf4j;

/**
 * 基于 Redis 的 Token 吊销存储，供 auth、market、admin 等服务共享。
 * <p>
 * 通过反射调用 Redisson，避免 domain 模块编译期依赖 Redisson。
 * 启用 {@code token-revocation.redis.enabled=true} 时需提供 {@code org.redisson.api.RedissonClient} Bean。
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
            // 失败即拒绝：Redis 异常时视为已吊销，防止绕过登出/黑名单
            log.error("[RedisTokenRevocationService] failed to check jti:{} - denying token", jti, e);
            return true;
        }
    }

    @Override
    public long size() {
        return -1;
    }
}

package com.dyx.market.domain.auth.service;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * In-memory token revocation store for local development and tests.
 *
 * Expired entries are cleaned periodically to bound memory. Not suitable for
 * multi-instance production (use RedisTokenRevocationService in that case).
 */
@Slf4j
public class InMemoryTokenRevocationService implements ITokenRevocationService {

    private final ConcurrentHashMap<String, Long> revokedJtis = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleaner = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "token-revocation-cleaner");
        t.setDaemon(true);
        return t;
    });

    public InMemoryTokenRevocationService() {
        cleaner.scheduleWithFixedDelay(this::evictExpired, 60, 60, TimeUnit.SECONDS);
        log.info("[InMemoryTokenRevocationService] started with periodic eviction every 60s");
    }

    @Override
    public void revoke(String jti, long expiresAtMillis) {
        revokedJtis.put(jti, expiresAtMillis);
        log.info("[InMemoryTokenRevocationService] revoked jti:{}", jti);
    }

    @Override
    public boolean isRevoked(String jti) {
        Long expiresAt = revokedJtis.get(jti);
        if (expiresAt == null) return false;
        if (System.currentTimeMillis() > expiresAt) {
            revokedJtis.remove(jti);
            return false;
        }
        return true;
    }

    @Override
    public long size() {
        return revokedJtis.size();
    }

    private void evictExpired() {
        long now = System.currentTimeMillis();
        revokedJtis.entrySet().removeIf(e -> {
            boolean expired = now > e.getValue();
            if (expired) {
                log.debug("[InMemoryTokenRevocationService] evicted expired jti:{}", e.getKey());
            }
            return expired;
        });
    }
}

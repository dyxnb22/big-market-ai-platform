package com.dyx.market.gateway.config;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 内存令牌桶，供网关 IP+路径限流使用。
 */
public class TokenBucket {

    final long capacity;
    final double refillRatePerMs;
    final AtomicLong tokens;
    volatile long lastRefill;

    public TokenBucket(long capacity, double replenishRatePerSec) {
        this.capacity = capacity;
        this.refillRatePerMs = replenishRatePerSec / 1000.0;
        this.tokens = new AtomicLong(capacity);
        this.lastRefill = System.currentTimeMillis();
    }

    public long getTokenCount() {
        return tokens.get();
    }

    public boolean tryConsume(long requested) {
        refill();
        while (true) {
            long current = tokens.get();
            if (current < requested) {
                return false;
            }
            if (tokens.compareAndSet(current, current - requested)) {
                return true;
            }
        }
    }

    synchronized void refill() {
        long now = System.currentTimeMillis();
        long elapsed = now - lastRefill;
        if (elapsed <= 0) {
            return;
        }
        double newTokens = elapsed * refillRatePerMs;
        if (newTokens < 1.0) {
            return;
        }
        lastRefill = now;
        while (true) {
            long current = tokens.get();
            long next = Math.min((long) (current + newTokens), capacity);
            if (tokens.compareAndSet(current, next)) {
                break;
            }
        }
    }
}

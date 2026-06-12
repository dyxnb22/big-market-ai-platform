package com.dyx.market.gateway.config;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Value;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Provides a custom IpPathRateLimit GatewayFilterFactory for rate limiting
 * per (client IP + path prefix) using an in-memory token bucket.
 *
 * Usage in application.yml:
 *   filters:
 *     - name: IpPathRateLimit
 *       args:
 *         replenishRate: 20
 *         burstCapacity: 40
 *
 * This is a lightweight alternative to the Spring Cloud Gateway
 * RequestRateLimiter which requires Redis (spring-boot-starter-data-redis-reactive).
 *
 * SAFETY: Does not touch existing circuit breaker config.
 * DISABLE: Remove the filter from route definitions.
 */
@Configuration
public class RateLimiterConfig {

    @Bean
    public IpPathRateLimitGatewayFilterFactory ipPathRateLimitGatewayFilterFactory(
            @Value("${gateway.rate-limiter.enabled:false}") boolean enabled) {
        return new IpPathRateLimitGatewayFilterFactory(enabled);
    }

    /**
     * Custom GatewayFilterFactory that applies an in-memory token-bucket
     * rate limit per key derived from client IP + path prefix.
     */
    public static class IpPathRateLimitGatewayFilterFactory
            extends AbstractGatewayFilterFactory<IpPathRateLimitGatewayFilterFactory.Config> {

        private static final Log log = LogFactory.getLog(IpPathRateLimitGatewayFilterFactory.class);
        private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();
        private final boolean enabled;

        public IpPathRateLimitGatewayFilterFactory(boolean enabled) {
            super(Config.class);
            this.enabled = enabled;
        }

        @Override
        public List<String> shortcutFieldOrder() {
            return Collections.singletonList("replenishRate");
        }

        @Override
        public GatewayFilter apply(Config config) {
            return (exchange, chain) -> {
                if (!enabled) {
                    return chain.filter(exchange);
                }
                String ip = exchange.getRequest().getRemoteAddress() != null
                        ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                        : "unknown";
                String path = exchange.getRequest().getURI().getPath();
                String[] segments = path.split("/");
                String route = segments.length >= 3 ? "/" + segments[1] + "/" + segments[2] : path;
                String key = ip + ":" + route;

                TokenBucket bucket = buckets.computeIfAbsent(key,
                        k -> new TokenBucket(config.burstCapacity, config.replenishRate));

                if (!bucket.tryConsume(1)) {
                    if (log.isDebugEnabled()) {
                        log.debug("Rate limit exceeded for key: " + key);
                    }
                    exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                    return exchange.getResponse().setComplete();
                }
                return chain.filter(exchange);
            };
        }

        public static class Config {
            private int replenishRate = 10;
            private int burstCapacity = 20;

            public int getReplenishRate() { return replenishRate; }
            public void setReplenishRate(int replenishRate) { this.replenishRate = replenishRate; }
            public int getBurstCapacity() { return burstCapacity; }
            public void setBurstCapacity(int burstCapacity) { this.burstCapacity = burstCapacity; }
        }

        static class TokenBucket {
            final long capacity;
            final double refillRatePerMs;
            final AtomicLong tokens;
            volatile long lastRefill;

            TokenBucket(long capacity, double replenishRatePerSec) {
                this.capacity = capacity;
                this.refillRatePerMs = replenishRatePerSec / 1000.0;
                this.tokens = new AtomicLong(capacity);
                this.lastRefill = System.currentTimeMillis();
            }

            boolean tryConsume(long requested) {
                refill();
                while (true) {
                    long current = tokens.get();
                    if (current < requested) return false;
                    if (tokens.compareAndSet(current, current - requested)) return true;
                }
            }

            void refill() {
                long now = System.currentTimeMillis();
                long elapsed = now - lastRefill;
                if (elapsed <= 0) return;
                double newTokens = elapsed * refillRatePerMs;
                if (newTokens < 1.0) return;
                lastRefill = now;
                while (true) {
                    long current = tokens.get();
                    long next = Math.min((long) (current + newTokens), capacity);
                    if (tokens.compareAndSet(current, next)) break;
                }
            }
        }
    }
}

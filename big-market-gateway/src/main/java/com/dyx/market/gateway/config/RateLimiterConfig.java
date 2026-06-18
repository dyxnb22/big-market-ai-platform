package com.dyx.market.gateway.config;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Value;

import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 自定义路由级限流过滤器 {@code IpPathRateLimit}（内存令牌桶）。
 * <p>
 * 按「客户端 IP + 路径前缀（如 /api/v1）」限流，无需 Redis。
 * 在 application-docker.yml 中挂到 auth/market 路由；总开关为
 * {@code gateway.rate-limiter.enabled}（默认 false，dev 环境通常不限流）。
 * <p>
 * yml 用法：
 * <pre>
 * filters:
 *   - name: IpPathRateLimit
 *     args:
 *       replenishRate: 20
 *       burstCapacity: 40
 * </pre>
 */
@Configuration
public class RateLimiterConfig {

    @Bean
    public IpPathRateLimitGatewayFilterFactory ipPathRateLimitGatewayFilterFactory(
            @Value("${gateway.rate-limiter.enabled:false}") boolean enabled) {
        return new IpPathRateLimitGatewayFilterFactory(enabled);
    }

    /**
     * 类名去掉 {@code GatewayFilterFactory} 后缀后即为 yml 中的过滤器名：IpPathRateLimit。
     */
    public static class IpPathRateLimitGatewayFilterFactory
            extends AbstractGatewayFilterFactory<IpPathRateLimitGatewayFilterFactory.Config> {

        private static final Log log = LogFactory.getLog(IpPathRateLimitGatewayFilterFactory.class);
        /** 每个限流 key 对应一个令牌桶，仅当前网关进程有效，多实例不共享 */
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
                InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
                String ip = remoteAddress != null && remoteAddress.getAddress() != null
                        ? remoteAddress.getAddress().getHostAddress()
                        : "unknown";
                String path = exchange.getRequest().getURI().getPath();
                String[] segments = path.split("/");
                // 例：/api/v1/auth/login → /api/v1，同 IP 下同版本 API 共享配额
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

        /** 路由 yml 中的 replenishRate / burstCapacity 会绑定到此 */
        public static class Config {
            private int replenishRate = 10;
            private int burstCapacity = 20;

            public int getReplenishRate() { return replenishRate; }
            public void setReplenishRate(int replenishRate) { this.replenishRate = replenishRate; }
            public int getBurstCapacity() { return burstCapacity; }
            public void setBurstCapacity(int burstCapacity) { this.burstCapacity = burstCapacity; }
        }

        /** 令牌桶：capacity 为突发上限，replenishRatePerSec 为每秒补充速率 */
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

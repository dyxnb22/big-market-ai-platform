package com.dyx.market.gateway.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Value;

import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 自定义路由级限流过滤器 {@code IpPathRateLimit}（内存令牌桶）。
 */
@Configuration
public class RateLimiterConfig {

    private static final int MAX_BUCKETS = 10_000;

    @Bean
    public IpPathRateLimitGatewayFilterFactory ipPathRateLimitGatewayFilterFactory(
            @Value("${gateway.rate-limiter.enabled:false}") boolean enabled) {
        return new IpPathRateLimitGatewayFilterFactory(enabled);
    }

    @Slf4j
    public static class IpPathRateLimitGatewayFilterFactory
            extends AbstractGatewayFilterFactory<IpPathRateLimitGatewayFilterFactory.Config> {

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
                evictIfNeeded();
                InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
                String ip = remoteAddress != null && remoteAddress.getAddress() != null
                        ? remoteAddress.getAddress().getHostAddress()
                        : "unknown";
                String path = exchange.getRequest().getURI().getPath();
                String[] segments = path.split("/");
                String route = segments.length >= 3 ? "/" + segments[1] + "/" + segments[2] : path;
                String key = ip + ":" + route;

                TokenBucket bucket = buckets.computeIfAbsent(key,
                        k -> new TokenBucket(config.burstCapacity, config.replenishRate));

                if (!bucket.tryConsume(1)) {
                    log.debug("Rate limit exceeded for key: {}", key);
                    exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                    return exchange.getResponse().setComplete();
                }
                return chain.filter(exchange);
            };
        }

        private void evictIfNeeded() {
            if (buckets.size() <= MAX_BUCKETS) {
                return;
            }
            // 优先淘汰已完全耗尽的令牌桶（tokens==0），避免重置仍在使用的正常用户；只有耗尽
            // 桶不足以将 Map 降到上限以下时，才进行任意淘汰。
            buckets.entrySet().removeIf(e -> e.getValue().getTokenCount() <= 0);
            if (buckets.size() <= MAX_BUCKETS) {
                return;
            }
            Iterator<Map.Entry<String, TokenBucket>> it = buckets.entrySet().iterator();
            int remove = buckets.size() - MAX_BUCKETS;
            while (remove > 0 && it.hasNext()) {
                it.next();
                it.remove();
                remove--;
            }
        }

        public static class Config {
            private int replenishRate = 10;
            private int burstCapacity = 20;

            public int getReplenishRate() { return replenishRate; }
            public void setReplenishRate(int replenishRate) { this.replenishRate = replenishRate; }
            public int getBurstCapacity() { return burstCapacity; }
            public void setBurstCapacity(int burstCapacity) { this.burstCapacity = burstCapacity; }
        }
    }
}

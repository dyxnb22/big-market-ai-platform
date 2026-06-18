package com.dyx.market.market.config;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Guava 本地缓存配置，用于短时热点数据（默认写后 3 秒过期）。
 */
@Configuration
public class GuavaConfig {

    @Bean(name = "cache")
    public Cache<String, String> cache() {
        return CacheBuilder.newBuilder()
                .expireAfterWrite(3, TimeUnit.SECONDS)
                .build();
    }

}

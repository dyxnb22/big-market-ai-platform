package com.dyx.market.admin.service.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Redis 连接配置，绑定前缀 {@code redis.sdk.config}。
 */
@Data
@ConfigurationProperties(prefix = "redis.sdk.config", ignoreInvalidFields = true)
public class RedisClientConfigProperties {

    private String host;
    private int port;
    private String password;
    private int poolSize = 64;
    private int minIdleSize = 10;
    private int idleTimeout = 10000;
    private int connectTimeout = 10000;
    private int retryAttempts = 3;
    private int retryInterval = 1000;
    private int pingInterval = 0;
    private boolean keepAlive = true;

}

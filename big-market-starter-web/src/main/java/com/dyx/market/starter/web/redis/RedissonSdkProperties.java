package com.dyx.market.starter.web.redis;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "redis.sdk.config", ignoreInvalidFields = true)
public class RedissonSdkProperties implements RedissonConnectionProperties {

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

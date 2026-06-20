package com.dyx.market.starter.web.redis;

/**
 * Redisson 单机连接参数契约，由各服务的 {@code RedisClientConfigProperties} 实现。
 */
public interface RedissonConnectionProperties {

    String getHost();

    int getPort();

    String getPassword();

    int getPoolSize();

    int getMinIdleSize();

    int getIdleTimeout();

    int getConnectTimeout();

    int getRetryAttempts();

    int getRetryInterval();

    int getPingInterval();

    boolean isKeepAlive();
}

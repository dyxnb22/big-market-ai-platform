package com.dyx.market.starter.web.redis;

import org.apache.commons.lang3.StringUtils;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;

/**
 * 统一 Redisson 单机连接参数绑定，避免各服务复制粘贴时遗漏 password 等字段。
 */
public final class RedissonSingleServerSupport {

    private RedissonSingleServerSupport() {
    }

    public static void apply(Config config, RedissonConnectionProperties properties) {
        SingleServerConfig server = config.useSingleServer()
                .setAddress("redis://" + properties.getHost() + ":" + properties.getPort())
                .setConnectionPoolSize(properties.getPoolSize())
                .setConnectionMinimumIdleSize(properties.getMinIdleSize())
                .setIdleConnectionTimeout(properties.getIdleTimeout())
                .setConnectTimeout(properties.getConnectTimeout())
                .setRetryAttempts(properties.getRetryAttempts())
                .setRetryInterval(properties.getRetryInterval())
                .setPingConnectionInterval(properties.getPingInterval())
                .setKeepAlive(properties.isKeepAlive());
        if (StringUtils.isNotBlank(properties.getPassword())) {
            server.setPassword(properties.getPassword());
        }
    }
}

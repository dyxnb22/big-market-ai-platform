package com.dyx.market.starter.dcc.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 动态配置中心（DCC）Zookeeper 连接配置。
 *
 * <p>绑定前缀：{@code zookeeper.sdk.config}</p>
 */
@ConfigurationProperties(prefix = "zookeeper.sdk.config", ignoreInvalidFields = true)
public class DccProperties {

    /** 是否启用基于 Zookeeper 的动态配置中心。 */
    private boolean enable;
    /** Zookeeper 连接地址，如 127.0.0.1:2181。 */
    private String connectString;
    /** 重试基础休眠时间（毫秒）。 */
    private int baseSleepTimeMs;
    /** 最大重试次数。 */
    private int maxRetries;
    /** 会话超时时间（毫秒）。 */
    private int sessionTimeoutMs;
    /** 连接超时时间（毫秒）。 */
    private int connectionTimeoutMs;

    public boolean isEnable() {
        return enable;
    }

    public void setEnable(boolean enable) {
        this.enable = enable;
    }

    public String getConnectString() {
        return connectString;
    }

    public void setConnectString(String connectString) {
        this.connectString = connectString;
    }

    public int getBaseSleepTimeMs() {
        return baseSleepTimeMs;
    }

    public void setBaseSleepTimeMs(int baseSleepTimeMs) {
        this.baseSleepTimeMs = baseSleepTimeMs;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public int getSessionTimeoutMs() {
        return sessionTimeoutMs;
    }

    public void setSessionTimeoutMs(int sessionTimeoutMs) {
        this.sessionTimeoutMs = sessionTimeoutMs;
    }

    public int getConnectionTimeoutMs() {
        return connectionTimeoutMs;
    }

    public void setConnectionTimeoutMs(int connectionTimeoutMs) {
        this.connectionTimeoutMs = connectionTimeoutMs;
    }
}

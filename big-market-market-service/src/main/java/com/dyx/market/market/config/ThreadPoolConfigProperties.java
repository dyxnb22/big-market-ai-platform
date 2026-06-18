package com.dyx.market.market.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 线程池参数，绑定配置前缀 {@code thread.pool.executor.config}。
 */
@Data
@ConfigurationProperties(prefix = "thread.pool.executor.config", ignoreInvalidFields = true)
public class ThreadPoolConfigProperties {

    private Integer corePoolSize = 20;
    private Integer maxPoolSize = 200;
    private Long keepAliveTime = 10L;
    private Integer blockQueueSize = 5000;
    private String policy = "AbortPolicy";

}

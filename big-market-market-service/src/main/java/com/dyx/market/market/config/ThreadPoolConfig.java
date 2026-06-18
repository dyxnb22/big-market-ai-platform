package com.dyx.market.market.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.*;

/**
 * 通用线程池配置，绑定 {@link ThreadPoolConfigProperties}（前缀 {@code thread.pool.executor.config}）。
 * <p>
 * 若容器中尚无 {@link ThreadPoolExecutor} Bean，则按配置创建（供 {@code @Async} 等使用）。
 */
@Slf4j
@EnableAsync
@Configuration
@EnableConfigurationProperties(ThreadPoolConfigProperties.class)
public class ThreadPoolConfig {

    @Bean
    @ConditionalOnMissingBean(ThreadPoolExecutor.class)
    public ThreadPoolExecutor threadPoolExecutor(ThreadPoolConfigProperties properties) {
        RejectedExecutionHandler handler;
        switch (properties.getPolicy()) {
            case "AbortPolicy":         handler = new ThreadPoolExecutor.AbortPolicy(); break;
            case "DiscardPolicy":       handler = new ThreadPoolExecutor.DiscardPolicy(); break;
            case "DiscardOldestPolicy": handler = new ThreadPoolExecutor.DiscardOldestPolicy(); break;
            case "CallerRunsPolicy":    handler = new ThreadPoolExecutor.CallerRunsPolicy(); break;
            default:                    handler = new ThreadPoolExecutor.AbortPolicy(); break;
        }
        return new ThreadPoolExecutor(
                properties.getCorePoolSize(),
                properties.getMaxPoolSize(),
                properties.getKeepAliveTime(),
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(properties.getBlockQueueSize()),
                Executors.defaultThreadFactory(),
                handler);
    }

}

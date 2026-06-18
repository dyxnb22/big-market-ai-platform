package com.dyx.market.account.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.*;

/**
 * 通用 {@link ThreadPoolExecutor} 配置，供 {@code @Async} 等异步任务使用。
 * <p>
 * 参数来自 {@link ThreadPoolConfigProperties}；仅当容器中尚无
 * {@link ThreadPoolExecutor} Bean 时注册，避免覆盖其他模块定义。
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
        // 拒绝策略与 JDK 内置 Handler 名称一一对应
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

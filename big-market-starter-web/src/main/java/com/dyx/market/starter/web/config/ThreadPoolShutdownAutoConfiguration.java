package com.dyx.market.starter.web.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 线程池优雅关闭自动配置，应用退出时等待任务完成后关闭 {@link ThreadPoolExecutor}。
 */
@Slf4j
@Configuration
@ConditionalOnBean(ThreadPoolExecutor.class)
public class ThreadPoolShutdownAutoConfiguration {

    @Bean
    public DisposableBean threadPoolExecutorShutdownHook(ThreadPoolExecutor threadPoolExecutor) {
        return () -> {
            threadPoolExecutor.shutdown();
            try {
                if (!threadPoolExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                    threadPoolExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                threadPoolExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            log.info("ThreadPoolExecutor shut down gracefully");
        };
    }
}

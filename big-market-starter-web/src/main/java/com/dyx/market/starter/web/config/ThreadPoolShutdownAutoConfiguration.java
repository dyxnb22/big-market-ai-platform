package com.dyx.market.starter.web.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

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

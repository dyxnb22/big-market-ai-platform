package com.dyx.market.account.config;

import io.micrometer.core.aop.CountedAspect;
import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.prometheus.client.CollectorRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * Prometheus / Micrometer 指标配置。
 * <p>
 * 注册 {@link PrometheusMeterRegistry} 与 {@link TimedAspect}、{@link CountedAspect}，
 * 支持 {@code @Timed}、{@code @Counted} 注解采集方法耗时与调用次数。
 */
@EnableAspectJAutoProxy
@Configuration
public class PrometheusConfiguration {

    @Bean
    public CollectorRegistry collectorRegistry() {
        return new CollectorRegistry();
    }

    @Bean
    public PrometheusMeterRegistry prometheusMeterRegistry(PrometheusConfig config, CollectorRegistry collectorRegistry) {
        return new PrometheusMeterRegistry(config, collectorRegistry, Clock.SYSTEM);
    }

    @Bean
    public TimedAspect timedAspect(MeterRegistry registry) {
        return new TimedAspect(registry);
    }

    @Bean
    public CountedAspect countedAspect(MeterRegistry registry) {
        return new CountedAspect(registry);
    }

}

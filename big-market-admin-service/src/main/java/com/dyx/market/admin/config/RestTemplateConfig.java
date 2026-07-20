package com.dyx.market.admin.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/** 管理端访问 Nacos 等外部 HTTP 服务的统一客户端配置。 */
@Configuration
public class RestTemplateConfig {

    /**
     * 创建带有限连接/读取超时的客户端，避免配置中心异常时阻塞 Admin 请求线程。
     */
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .connectTimeout(Duration.ofSeconds(5))
                .readTimeout(Duration.ofSeconds(10))
                .build();
    }
}

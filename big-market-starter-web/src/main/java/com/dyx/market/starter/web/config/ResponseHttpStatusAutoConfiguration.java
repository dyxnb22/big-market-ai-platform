package com.dyx.market.starter.web.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Servlet Web 应用的响应码自动配置。
 *
 * <p>将统一响应体中的业务码映射为 HTTP 状态码；非 Servlet 应用不加载该 Bean。</p>
 */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class ResponseHttpStatusAutoConfiguration {

    /** 注册统一响应状态码 Advice。 */
    @Bean
    public ResponseHttpStatusAdvice responseHttpStatusAdvice() {
        return new ResponseHttpStatusAdvice();
    }
}

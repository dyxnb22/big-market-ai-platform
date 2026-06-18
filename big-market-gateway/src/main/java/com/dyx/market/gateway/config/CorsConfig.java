package com.dyx.market.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

/**
 * 跨域（CORS）配置，注册 WebFlux {@link CorsWebFilter}。
 * <p>
 * 适用场景：本地开发时前端（如 localhost:3000）直连网关（8080）的跨域请求。
 * OPTIONS 预检在网关层直接响应，不会转发到下游微服务。
 * <p>
 * 经 nginx 同源部署时浏览器不触发 CORS，此配置基本不生效。
 * <p>
 * 允许的来源由 {@code app.cors.allowed-origins} 配置，默认 {@code *}。
 */
@Configuration
public class CorsConfig {

    @Value("${app.cors.allowed-origins:*}")
    private String allowedOrigins;

    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration config = new CorsConfiguration();
        // "*" 须用 allowedOriginPattern；具体域名列表用 allowedOrigins 精确匹配
        if ("*".equals(allowedOrigins)) {
            config.addAllowedOriginPattern("*");
        } else {
            Arrays.stream(allowedOrigins.split(","))
                    .map(String::trim)
                    .forEach(config::addAllowedOrigin);
        }
        config.addAllowedMethod("*");
        config.addAllowedHeader("*");
        // 与通配符来源搭配时 Spring 要求 credentials 为 false
        config.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsWebFilter(source);
    }

}

package com.dyx.market.gateway.config;

import com.dyx.market.types.common.CorsSettings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

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
        CorsSettings.apply(config, allowedOrigins);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsWebFilter(source);
    }

}

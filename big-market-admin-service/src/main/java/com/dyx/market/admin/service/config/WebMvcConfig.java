package com.dyx.market.admin.service.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;
import java.util.Arrays;

/**
 * Web MVC 配置：CORS 跨域与管理员鉴权拦截器注册。
 * <p>
 * CORS 允许来源绑定 {@code app.cors.allowed-origins}（默认 {@code *}）。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Resource
    private AdminAuthInterceptor adminAuthInterceptor;

    @Value("${app.cors.allowed-origins:*}")
    private String allowedOrigins;

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        if ("*".equals(allowedOrigins)) {
            config.addAllowedOriginPattern("*");
        } else {
            Arrays.stream(allowedOrigins.split(","))
                    .map(String::trim)
                    .forEach(config::addAllowedOrigin);
        }
        config.addAllowedMethod("*");
        config.addAllowedHeader("*");
        config.setAllowCredentials(false);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(adminAuthInterceptor)
                .addPathPatterns("/api/*/admin/**")
                .addPathPatterns("/api/*/raffle/erp/**")
                .addPathPatterns("/api/*/raffle/dcc/**");
    }

}

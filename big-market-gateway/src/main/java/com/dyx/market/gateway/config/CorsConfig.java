package com.dyx.market.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

/**
 * Global CORS configuration for the API gateway.
 *
 * Allows browser-based frontend (localhost:5173, :3000, etc.) to call the
 * gateway without same-origin policy issues. The preflight OPTIONS response
 * is handled at the gateway level and does NOT reach downstream services.
 *
 * When deployed via nginx (same-origin), CORS is not exercised by the browser
 * — this config applies only to cross-origin dev scenarios.
 *
 * Allowed origins are read from the app.cors.allowed-origins property.
 * Defaults to allow-all (*) for local dev; set to specific origins in production.
 */
@Configuration
public class CorsConfig {

    @Value("${app.cors.allowed-origins:*}")
    private String allowedOrigins;

    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration config = new CorsConfiguration();
        // When a specific list is configured, use setAllowedOrigins (exact match).
        // When "*" is configured, use setAllowedOriginPattern to keep compatibility.
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
        return new CorsWebFilter(source);
    }

}

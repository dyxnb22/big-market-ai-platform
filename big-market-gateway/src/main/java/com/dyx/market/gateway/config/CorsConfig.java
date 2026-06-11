package com.dyx.market.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

/**
 * Global CORS configuration for the API gateway.
 *
 * Allows browser-based frontend (localhost:5173, :3000, etc.) to call the
 * gateway without same-origin policy issues. The preflight OPTIONS response
 * is handled at the gateway level and does NOT reach downstream services.
 *
 * When deployed via nginx (same-origin), CORS is not exercised by the browser
 * — this config applies only to cross-origin dev scenarios.
 */
@Configuration
public class CorsConfig {

    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.addAllowedOriginPattern("*");
        config.addAllowedMethod("*");
        config.addAllowedHeader("*");
        config.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsWebFilter(source);
    }

}

package com.dyx.market.types.common;

import org.springframework.web.cors.CorsConfiguration;

import java.util.Arrays;

/**
 * 共享 CORS 配置（Servlet 与 WebFlux 均使用 {@link CorsConfiguration}）。
 */
public final class CorsSettings {

    private CorsSettings() {
    }

    public static void apply(CorsConfiguration config, String allowedOrigins) {
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
    }
}

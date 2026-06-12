package com.dyx.market.market.config;

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

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Resource
    private TokenAuthInterceptor tokenAuthInterceptor;

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
        registry.addInterceptor(tokenAuthInterceptor)
                .addPathPatterns(
                        "/api/*/raffle/activity/draw_by_token",
                        "/api/*/raffle/activity/calendar_sign_rebate_by_token",
                        "/api/*/raffle/activity/is_calendar_sign_rebate_by_token",
                        "/api/*/raffle/activity/query_user_activity_account_by_token",
                        "/api/*/raffle/activity/query_user_credit_account_by_token",
                        "/api/*/raffle/activity/credit_pay_exchange_sku_by_token",
                        "/api/*/raffle/activity/chat_credit_deduct_by_token",
                        "/api/*/raffle/activity/chat_credit_refund_by_token",
                        "/api/*/raffle/strategy/query_raffle_award_list_by_token")
                .excludePathPatterns("/api/*/auth/**");
    }

}

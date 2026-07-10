package com.dyx.market.market.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;

/**
 * Web MVC 配置：Token 鉴权与运营端鉴权拦截器注册。
 * <p>
 * CORS 由 {@code big-market-starter-web} 的 {@link com.dyx.market.starter.web.config.CorsAutoConfiguration} 统一提供。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Resource
    private TokenAuthInterceptor tokenAuthInterceptor;

    @Resource
    private OperationalAuthInterceptor operationalAuthInterceptor;

    @Resource
    private InternalChatServiceAuthInterceptor internalChatServiceAuthInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(internalChatServiceAuthInterceptor)
                .addPathPatterns(
                        "/api/*/internal/raffle/activity/chat_credit_refund_by_token",
                        "/api/*/internal/raffle/activity/chat_credit_mark_refund_pending_by_token");
        registry.addInterceptor(operationalAuthInterceptor)
                .addPathPatterns(
                        "/api/*/raffle/erp/**",
                        "/api/*/raffle/dcc/**",
                        "/api/*/raffle/activity/armory",
                        "/api/*/raffle/strategy/strategy_armory");
        registry.addInterceptor(tokenAuthInterceptor)
                .addPathPatterns(
                        "/api/*/raffle/activity/draw_by_token",
                        "/api/*/raffle/activity/calendar_sign_rebate_by_token",
                        "/api/*/raffle/activity/is_calendar_sign_rebate_by_token",
                        "/api/*/raffle/activity/query_user_activity_account_by_token",
                        "/api/*/raffle/activity/query_user_credit_account_by_token",
                        "/api/*/raffle/activity/credit_pay_exchange_sku_by_token",
                        "/api/*/raffle/activity/chat_credit_deduct_by_token",
                        "/api/*/raffle/strategy/query_raffle_award_list_by_token")
                .excludePathPatterns("/api/*/auth/**");
    }

}

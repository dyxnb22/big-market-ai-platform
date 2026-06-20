package com.dyx.market.admin.service.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;

/**
 * Web MVC 配置：管理员鉴权拦截器注册。
 * <p>
 * CORS 由 {@code big-market-starter-web} 的 {@link com.dyx.market.starter.web.config.CorsAutoConfiguration} 统一提供。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Resource
    private AdminAuthInterceptor adminAuthInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(adminAuthInterceptor)
                .addPathPatterns("/api/*/admin/**")
                .excludePathPatterns("/api/*/admin/config/public/**");
    }

}

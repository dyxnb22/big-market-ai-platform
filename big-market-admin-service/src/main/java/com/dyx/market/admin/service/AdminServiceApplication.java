package com.dyx.market.admin.service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 管理后台服务启动入口：平台配置 CRUD，基于 JWT 的管理员鉴权。
 * <p>
 * {@code PlatformConfigService} 以 Nacos 为唯一配置来源，并在进程内保留只读快照。
 */
@SpringBootApplication(scanBasePackages = {
        "com.dyx.market.admin.service",   // 本模块配置（拦截器、WebMvcConfig）
        "com.dyx.market.admin",           // AdminConfigController
        "com.dyx.market.management",      // PlatformConfigService
        "com.dyx.market.domain.auth"      // 无状态 JWT AuthService，供拦截器使用
})
public class AdminServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdminServiceApplication.class, args);
    }

}

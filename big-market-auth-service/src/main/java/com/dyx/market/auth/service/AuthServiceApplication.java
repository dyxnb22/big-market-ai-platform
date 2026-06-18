package com.dyx.market.auth.service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 认证服务启动入口：无状态 JWT 登录与 Token 校验。
 * <p>
 * 仅扫描 auth 控制器与 domain.auth 包，避免引入其他领域子包中无法解析的 Bean。
 */
@SpringBootApplication(scanBasePackages = {
        "com.dyx.market.auth.service",   // 本模块配置
        "com.dyx.market.auth",           // AuthAccessController
        "com.dyx.market.domain.auth"     // 无状态 JWT AuthService
})
public class AuthServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }

}

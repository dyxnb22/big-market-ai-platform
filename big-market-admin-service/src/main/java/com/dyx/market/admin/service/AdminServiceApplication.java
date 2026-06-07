package com.dyx.market.admin.service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Admin service: platform config CRUD with JWT-based admin auth.
 * PlatformConfigService is file-backed in-memory; no DB required.
 */
@SpringBootApplication(scanBasePackages = {
        "com.dyx.market.admin.service",   // this module's config (interceptor, WebMvcConfig)
        "com.dyx.market.admin",           // big-market-admin AdminConfigController
        "com.dyx.market.management",      // PlatformConfigService
        "com.dyx.market.domain.auth"      // stateless JWT AuthService for the interceptor
})
public class AdminServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdminServiceApplication.class, args);
    }

}

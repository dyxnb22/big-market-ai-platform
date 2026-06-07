package com.dyx.market.auth.service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Auth service: stateless JWT login and token validation.
 * Scans only auth controller and domain.auth packages to avoid pulling
 * unresolvable beans from other domain sub-packages.
 */
@SpringBootApplication(scanBasePackages = {
        "com.dyx.market.auth.service",   // this module's own config
        "com.dyx.market.auth",           // big-market-auth-access controller
        "com.dyx.market.domain.auth"     // stateless JWT AuthService in domain
})
public class AuthServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }

}

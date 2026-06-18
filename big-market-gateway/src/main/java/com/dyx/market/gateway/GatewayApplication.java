package com.dyx.market.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * API 网关启动入口（默认端口 8080）。
 * <p>
 * 职责：对外 HTTP 统一入口，按路径转发到 auth/admin/market/chatbot 等微服务。
 * 基于 Spring Cloud Gateway（WebFlux），不可引入 spring-boot-starter-web。
 */
@SpringBootApplication
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }

}

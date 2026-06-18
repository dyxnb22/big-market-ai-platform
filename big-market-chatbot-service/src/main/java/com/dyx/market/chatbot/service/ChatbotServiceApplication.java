package com.dyx.market.chatbot.service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 聊天机器人服务启动入口：基于规则 / DeepSeek 的智能助手。
 * <p>
 * 使用 {@code PlatformConfigService}（文件持久化内存配置），无需数据库或 Redis。
 */
@SpringBootApplication(scanBasePackages = {
        "com.dyx.market.chatbot.service",  // 本模块配置（RestTemplateConfig）
        "com.dyx.market.chatbot",          // ChatbotController
        "com.dyx.market.management"        // PlatformConfigService
})
public class ChatbotServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChatbotServiceApplication.class, args);
    }

}

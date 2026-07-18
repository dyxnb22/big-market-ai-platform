package com.dyx.market.chatbot.service;

import com.dyx.market.infrastructure.adapter.repository.ChatCreditSessionSupport;
import com.dyx.market.infrastructure.adapter.repository.ChatRequestIdempotencySupport;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/**
 * 聊天机器人服务启动入口：基于规则 / DeepSeek 的智能助手。
 * <p>
 * 使用 Nacos 驱动的 {@code PlatformConfigService} 快照与 Redis（requestId 幂等）。
 */
@SpringBootApplication(scanBasePackages = {
        "com.dyx.market.chatbot.service",
        "com.dyx.market.chatbot",
        "com.dyx.market.management",
        "com.dyx.market.infrastructure.dao",
        "com.dyx.market.infrastructure.redis"
})
@Import({ChatCreditSessionSupport.class, ChatRequestIdempotencySupport.class})
@org.mybatis.spring.annotation.MapperScan("com.dyx.market.infrastructure.dao")
public class ChatbotServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChatbotServiceApplication.class, args);
    }

}

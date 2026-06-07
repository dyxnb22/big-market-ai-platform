package com.dyx.market.chatbot.service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Chatbot service: rule-based / DeepSeek-backed assistant.
 * Uses PlatformConfigService (file-backed in-memory); no DB or Redis required.
 */
@SpringBootApplication(scanBasePackages = {
        "com.dyx.market.chatbot.service",  // this module's config (RestTemplateConfig)
        "com.dyx.market.chatbot",          // big-market-chatbot ChatbotController
        "com.dyx.market.management"        // PlatformConfigService
})
public class ChatbotServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChatbotServiceApplication.class, args);
    }

}

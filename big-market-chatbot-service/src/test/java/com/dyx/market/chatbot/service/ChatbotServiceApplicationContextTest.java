package com.dyx.market.chatbot.service;

import com.dyx.market.chatbot.application.ChatbotApplicationService;
import com.dyx.market.infrastructure.adapter.repository.ChatRequestIdempotencySupport;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import static org.junit.Assert.assertNotNull;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = ChatbotServiceApplication.class)
@ActiveProfiles("test")
public class ChatbotServiceApplicationContextTest {

    static {
        java.io.File dubboCache = new java.io.File("target/dubbo-cache");
        dubboCache.mkdirs();
        System.setProperty("dubbo.meta.cache.filePath", new java.io.File(dubboCache, "meta").getAbsolutePath());
        System.setProperty("dubbo.mapping.cache.filePath", new java.io.File(dubboCache, "mapping").getAbsolutePath());
    }

    @MockBean
    private RedissonClient redissonClient;

    @Autowired
    private ChatbotApplicationService chatbotApplicationService;

    @Autowired
    private ChatRequestIdempotencySupport chatRequestIdempotencySupport;

    @Test
    public void contextLoads_redisBackedIdempotencyBeansPresent() {
        assertNotNull(chatbotApplicationService);
        assertNotNull(chatRequestIdempotencySupport);
    }
}

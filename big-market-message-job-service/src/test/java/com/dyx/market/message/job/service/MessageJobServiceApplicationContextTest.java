package com.dyx.market.message.job.service;

import com.dyx.market.domain.chat.adapter.repository.IChatCreditSessionRepository;
import com.dyx.market.message.job.config.ChatRefundReconcileJob;
import com.dyx.market.message.job.config.MessageJobLocalAccountReadAdapter;
import com.dyx.market.message.job.config.OutboxSchemaValidator;
import com.dyx.market.trigger.adapter.IAccountReadAdapter;
import com.dyx.market.trigger.application.ChatCreditApplicationService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

/**
 * BM-002：message-job-service 完整 Spring 上下文必须加载 ChatCredit 对账 Bean。
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = MessageJobServiceApplication.class)
@ActiveProfiles("test")
public class MessageJobServiceApplicationContextTest {

    static {
        java.io.File dubboCache = new java.io.File("target/dubbo-cache");
        dubboCache.mkdirs();
        System.setProperty("dubbo.registry.file", new java.io.File(dubboCache, "registry.properties").getAbsolutePath());
        System.setProperty("dubbo.meta.cache.filePath", new java.io.File(dubboCache, "meta").getAbsolutePath());
        System.setProperty("dubbo.mapping.cache.filePath", new java.io.File(dubboCache, "mapping").getAbsolutePath());
    }

    @MockitoBean
    private RedissonClient redissonClient;

    @MockitoBean
    private OutboxSchemaValidator outboxSchemaValidator;

    @Autowired
    private ChatCreditApplicationService chatCreditApplicationService;

    @Autowired
    private IAccountReadAdapter accountReadAdapter;

    @Autowired
    private MessageJobLocalAccountReadAdapter messageJobLocalAccountReadAdapter;

    @Autowired
    private ChatRefundReconcileJob chatRefundReconcileJob;

    @Autowired
    private IChatCreditSessionRepository chatCreditSessionRepository;

    @Test
    public void contextLoads_requiredBeansPresent() {
        assertNotNull(chatCreditApplicationService);
        assertNotNull(accountReadAdapter);
        assertSame(messageJobLocalAccountReadAdapter, accountReadAdapter);
        assertNotNull(chatRefundReconcileJob);
        assertNotNull(chatCreditSessionRepository);
    }
}

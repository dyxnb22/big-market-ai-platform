package com.dyx.market.message.job.service;

import com.dyx.market.domain.activity.service.IRaffleActivityAccountQuotaService;
import com.dyx.market.domain.chat.adapter.repository.IChatCreditSessionRepository;
import com.dyx.market.domain.credit.service.ICreditAdjustService;
import com.dyx.market.message.job.config.MessageJobLocalAccountReadAdapter;
import com.dyx.market.trigger.adapter.IAccountCreditWriteAdapter;
import com.dyx.market.trigger.adapter.IAccountReadAdapter;
import com.dyx.market.trigger.application.ChatCreditApplicationService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;

/**
 * BM-002: message-job 为 ChatCredit 提供 {@link IAccountReadAdapter}，Context 可注入。
 */
@RunWith(SpringRunner.class)
@ContextConfiguration(classes = MessageJobServiceApplicationContextTest.ChatCreditContextConfig.class)
public class MessageJobServiceApplicationContextTest {

    @Autowired
    private ChatCreditApplicationService chatCreditApplicationService;

    @Autowired
    private IAccountReadAdapter accountReadAdapter;

    @Autowired
    private MessageJobLocalAccountReadAdapter messageJobLocalAccountReadAdapter;

    @Test
    public void contextLoads_requiredBeansPresent() {
        assertNotNull(chatCreditApplicationService);
        assertNotNull(accountReadAdapter);
        assertSame(messageJobLocalAccountReadAdapter, accountReadAdapter);
    }

    @Configuration
    @Import({MessageJobLocalAccountReadAdapter.class, ChatCreditApplicationService.class})
    static class ChatCreditContextConfig {

        @Bean
        @Primary
        ICreditAdjustService creditAdjustService() {
            return mock(ICreditAdjustService.class);
        }

        @Bean
        @Primary
        IRaffleActivityAccountQuotaService raffleActivityAccountQuotaService() {
            return mock(IRaffleActivityAccountQuotaService.class);
        }

        @Bean
        IChatCreditSessionRepository chatCreditSessionRepository() {
            return mock(IChatCreditSessionRepository.class);
        }

        @Bean
        IAccountCreditWriteAdapter accountCreditWriteAdapter() {
            return mock(IAccountCreditWriteAdapter.class);
        }
    }
}

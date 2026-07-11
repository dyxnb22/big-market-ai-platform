package com.dyx.market.message.job.config;

import com.dyx.market.domain.chat.adapter.repository.IChatCreditSessionRepository;
import com.dyx.market.infrastructure.dao.po.PendingRemoteWriteTask;
import com.dyx.market.trigger.application.CreditPayExchangeApplicationService;
import com.dyx.market.types.common.RemoteWriteOperations;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class RemoteWriteContinuationDispatcherTest {

    @Mock
    private CreditPayExchangeApplicationService creditPayExchangeApplicationService;
    @Mock
    private IChatCreditSessionRepository chatCreditSessionRepository;
    @InjectMocks
    private RemoteWriteContinuationDispatcher dispatcher;

    @Test
    public void creditCreate_chatDebit_marksDurableSessionDeducted() {
        PendingRemoteWriteTask task = PendingRemoteWriteTask.builder()
                .operation(RemoteWriteOperations.CREDIT_CREATE)
                .payload("{\"userId\":\"u_1\",\"tradeName\":\"OPENAI_PAY\",\"tradeType\":\"reverse\","
                        + "\"outBusinessNo\":\"chat_u_1_req_2\"}")
                .build();

        dispatcher.dispatch(task);

        verify(chatCreditSessionRepository).markDeducted("u_1", "req_2");
    }

    @Test
    public void creditCreate_nonChatTrade_doesNotTouchChatSession() {
        PendingRemoteWriteTask task = PendingRemoteWriteTask.builder()
                .operation(RemoteWriteOperations.CREDIT_CREATE)
                .payload("{\"userId\":\"u1\",\"tradeName\":\"AWARD_CREDIT\",\"tradeType\":\"forward\","
                        + "\"outBusinessNo\":\"award-1\"}")
                .build();

        dispatcher.dispatch(task);

        verify(chatCreditSessionRepository, never()).markDeducted(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }
}

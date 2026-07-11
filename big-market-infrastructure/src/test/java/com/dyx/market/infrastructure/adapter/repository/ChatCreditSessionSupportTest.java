package com.dyx.market.infrastructure.adapter.repository;

import com.dyx.market.infrastructure.dao.IChatCreditSessionDao;
import com.dyx.market.infrastructure.dao.po.ChatCreditSession;
import com.dyx.market.middleware.db.router.strategy.IDBRouterStrategy;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ChatCreditSessionSupportTest {

    @Mock
    private IChatCreditSessionDao chatCreditSessionDao;

    @Mock
    private IDBRouterStrategy dbRouter;

    @InjectMocks
    private ChatCreditSessionSupport support;

    @Test
    public void markRefundPending_passesUserIdToMapper() {
        when(chatCreditSessionDao.updateRefundState(any(ChatCreditSession.class))).thenReturn(1);

        support.markRefundPending("u1", "r1");

        ArgumentCaptor<ChatCreditSession> captor = ArgumentCaptor.forClass(ChatCreditSession.class);
        verify(chatCreditSessionDao).updateRefundState(captor.capture());
        assertEquals("u1", captor.getValue().getUserId());
        assertEquals("r1", captor.getValue().getRequestId());
        assertEquals(ChatCreditSessionSupport.REFUND_PENDING, captor.getValue().getRefundState());
        verify(dbRouter).doRouter("u1");
        verify(dbRouter).clear();
    }
}

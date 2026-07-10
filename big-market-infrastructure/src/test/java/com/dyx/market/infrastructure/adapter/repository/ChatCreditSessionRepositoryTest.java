package com.dyx.market.infrastructure.adapter.repository;

import com.dyx.market.infrastructure.dao.IChatCreditSessionDao;
import com.dyx.market.infrastructure.dao.po.ChatCreditSession;
import com.dyx.market.middleware.db.router.strategy.IDBRouterStrategy;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.dao.DuplicateKeyException;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ChatCreditSessionRepositoryTest {

    @Mock
    private IChatCreditSessionDao chatCreditSessionDao;

    @Mock
    private IDBRouterStrategy dbRouter;

    @InjectMocks
    private ChatCreditSessionRepository repository;

    @Test
    public void tryBeginRefund_acceptsPendingState() {
        when(chatCreditSessionDao.casRefundState("u1", "r1",
                ChatCreditSessionRepository.REFUND_NONE, ChatCreditSessionRepository.REFUND_REFUNDING)).thenReturn(0);
        when(chatCreditSessionDao.casRefundState("u1", "r1",
                ChatCreditSessionRepository.REFUND_PENDING, ChatCreditSessionRepository.REFUND_REFUNDING)).thenReturn(1);

        assertTrue(repository.tryBeginRefund("u1", "r1"));

        verify(dbRouter).doRouter("u1");
        verify(dbRouter).clear();
        verify(chatCreditSessionDao).casRefundState(eq("u1"), eq("r1"),
                eq(ChatCreditSessionRepository.REFUND_PENDING), eq(ChatCreditSessionRepository.REFUND_REFUNDING));
    }

    @Test
    public void recordDeduction_routesShardAndDoesNotUpdateOnDuplicate() {
        when(chatCreditSessionDao.insert(any(ChatCreditSession.class)))
                .thenThrow(new DuplicateKeyException("dup"));

        repository.recordDeduction("u1", "r1", 3);

        verify(dbRouter).doRouter("u1");
        verify(dbRouter).clear();
        verify(chatCreditSessionDao, never()).updateRefundState(any());
    }

    @Test
    public void markRefundPending_doesNotDowngradeRefunded() {
        when(chatCreditSessionDao.queryByUserIdAndRequestId("u1", "r1"))
                .thenReturn(ChatCreditSession.builder()
                        .userId("u1")
                        .requestId("r1")
                        .refundState(ChatCreditSessionRepository.REFUND_REFUNDED)
                        .build());

        repository.markRefundPending("u1", "r1", 2);

        verify(dbRouter).doRouter("u1");
        verify(dbRouter).clear();
        verify(chatCreditSessionDao, never()).casRefundState(any(), any(), any(), any());
        verify(chatCreditSessionDao, never()).updateRefundState(any());
    }

    @Test
    public void findSession_routesShard() {
        when(chatCreditSessionDao.queryByUserIdAndRequestId("u1", "r1"))
                .thenReturn(ChatCreditSession.builder()
                        .userId("u1")
                        .requestId("r1")
                        .deductAmount(1)
                        .deducted(true)
                        .refundState(ChatCreditSessionRepository.REFUND_NONE)
                        .build());

        assertNotNull(repository.findSession("u1", "r1"));

        verify(dbRouter).doRouter("u1");
        verify(dbRouter).clear();
    }

    @Test
    public void findSession_blankUserIdReturnsNull() {
        assertNull(repository.findSession("", "r1"));
        verify(dbRouter, never()).doRouter(any());
    }
}

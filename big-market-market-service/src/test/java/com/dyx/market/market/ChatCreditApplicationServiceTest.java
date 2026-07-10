package com.dyx.market.market;

import com.dyx.market.domain.chat.adapter.repository.IChatCreditSessionRepository;
import com.dyx.market.domain.chat.model.ChatCreditSessionSnapshot;
import com.dyx.market.trigger.adapter.IAccountCreditWriteAdapter;
import com.dyx.market.trigger.adapter.IAccountReadAdapter;
import com.dyx.market.trigger.application.ChatCreditApplicationService;
import com.dyx.market.types.enums.ResponseCode;
import com.dyx.market.types.exception.AppException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.math.BigDecimal;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * NR-001 + deduct intent-before-RPC.
 */
@RunWith(MockitoJUnitRunner.class)
public class ChatCreditApplicationServiceTest {

    @Mock
    private IAccountCreditWriteAdapter accountCreditWriteAdapter;
    @Mock
    private IAccountReadAdapter accountReadAdapter;
    @Mock
    private IChatCreditSessionRepository chatCreditSessionRepository;

    @InjectMocks
    private ChatCreditApplicationService chatCreditApplicationService;

    @Test(expected = AppException.class)
    public void refund_rejectsWhenNoSession() {
        when(chatCreditSessionRepository.findSession("user-1", "req-1")).thenReturn(null);
        try {
            chatCreditApplicationService.refund("user-1", "req-1");
        } catch (AppException e) {
            assertEquals(ResponseCode.ILLEGAL_PARAMETER.getCode(), e.getCode());
            verify(accountCreditWriteAdapter, never()).createOrder(any());
            throw e;
        }
    }

    @Test
    public void refund_usesSessionAmountNotClientInput() {
        when(chatCreditSessionRepository.findSession("user-1", "req-1"))
                .thenReturn(ChatCreditSessionSnapshot.builder()
                        .userId("user-1")
                        .requestId("req-1")
                        .deductAmount(3)
                        .deducted(true)
                        .refundState(IChatCreditSessionRepository.REFUND_NONE)
                        .build());
        when(chatCreditSessionRepository.tryBeginRefund("user-1", "req-1")).thenReturn(true);
        when(accountCreditWriteAdapter.createOrder(any())).thenReturn("order-1");
        when(accountReadAdapter.queryUserCreditAccount("user-1")).thenReturn(BigDecimal.TEN);

        BigDecimal balance = chatCreditApplicationService.refund("user-1", "req-1");

        assertEquals(BigDecimal.TEN, balance);
        verify(accountCreditWriteAdapter).createOrder(argThat(trade ->
                trade.getAmount().compareTo(BigDecimal.valueOf(3)) == 0
                        && "chat_refund_user-1_req-1".equals(trade.getOutBusinessNo())));
    }

    @Test
    public void refund_succeedsFromPendingState() {
        when(chatCreditSessionRepository.findSession("user-1", "req-pending"))
                .thenReturn(ChatCreditSessionSnapshot.builder()
                        .userId("user-1")
                        .requestId("req-pending")
                        .deductAmount(2)
                        .deducted(true)
                        .refundState(IChatCreditSessionRepository.REFUND_PENDING)
                        .build());
        when(chatCreditSessionRepository.tryBeginRefund("user-1", "req-pending")).thenReturn(true);
        when(accountCreditWriteAdapter.createOrder(any())).thenReturn("order-2");
        when(accountReadAdapter.queryUserCreditAccount("user-1")).thenReturn(BigDecimal.valueOf(8));

        BigDecimal balance = chatCreditApplicationService.refund("user-1", "req-pending");

        assertEquals(BigDecimal.valueOf(8), balance);
        verify(chatCreditSessionRepository).tryBeginRefund("user-1", "req-pending");
    }

    @Test
    public void markRefundPending_resolvesAmountFromSessionWhenZero() {
        when(chatCreditSessionRepository.findSession("user-1", "req-2"))
                .thenReturn(ChatCreditSessionSnapshot.builder()
                        .userId("user-1")
                        .requestId("req-2")
                        .deductAmount(5)
                        .deducted(true)
                        .refundState(IChatCreditSessionRepository.REFUND_NONE)
                        .build());

        chatCreditApplicationService.markRefundPending("user-1", "req-2", 0);

        verify(chatCreditSessionRepository).markRefundPending("user-1", "req-2", 5);
    }

    @Test
    public void deduct_recordsIntentBeforeRpc_andMarksDeductedOnSuccess() {
        when(accountCreditWriteAdapter.createOrder(any())).thenReturn("order-ok");
        when(accountReadAdapter.queryUserCreditAccount("user-1")).thenReturn(BigDecimal.TEN);

        BigDecimal balance = chatCreditApplicationService.deduct("user-1", 2, "req-ok");

        assertEquals(BigDecimal.TEN, balance);
        InOrder inOrder = inOrder(chatCreditSessionRepository, accountCreditWriteAdapter);
        inOrder.verify(chatCreditSessionRepository).recordDeductingIntent("user-1", "req-ok", 2);
        inOrder.verify(accountCreditWriteAdapter).createOrder(any());
        inOrder.verify(chatCreditSessionRepository).markDeducted("user-1", "req-ok");
    }

    @Test
    public void deduct_indexDupStillMarksDeducted() {
        when(accountCreditWriteAdapter.createOrder(any()))
                .thenThrow(new AppException(ResponseCode.INDEX_DUP.getCode(), "dup"));
        when(accountReadAdapter.queryUserCreditAccount("user-1")).thenReturn(BigDecimal.TEN);

        BigDecimal balance = chatCreditApplicationService.deduct("user-1", 2, "req-dup");

        assertEquals(BigDecimal.TEN, balance);
        verify(chatCreditSessionRepository).recordDeductingIntent("user-1", "req-dup", 2);
        verify(chatCreditSessionRepository).markDeducted("user-1", "req-dup");
    }

    @Test(expected = AppException.class)
    public void deduct_unknownErrorKeepsIntent() {
        when(accountCreditWriteAdapter.createOrder(any()))
                .thenThrow(new AppException(ResponseCode.UN_ERROR.getCode(), "timeout"));
        try {
            chatCreditApplicationService.deduct("user-1", 2, "req-unknown");
        } catch (AppException e) {
            verify(chatCreditSessionRepository).recordDeductingIntent("user-1", "req-unknown", 2);
            verify(chatCreditSessionRepository, never()).markDeductFailed(anyString(), anyString());
            verify(chatCreditSessionRepository, never()).markDeducted(anyString(), anyString());
            throw e;
        }
    }

    @Test(expected = AppException.class)
    public void deduct_illegalParameterMarksFailed() {
        when(accountCreditWriteAdapter.createOrder(any()))
                .thenThrow(new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "bad"));
        try {
            chatCreditApplicationService.deduct("user-1", 2, "req-reject");
        } catch (AppException e) {
            verify(chatCreditSessionRepository).markDeductFailed("user-1", "req-reject");
            throw e;
        }
    }
}

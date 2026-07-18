package com.dyx.market.chatbot.application;

import com.dyx.market.chatbot.client.MarketCreditGatewayClient;
import com.dyx.market.chatbot.support.ChatTokenUserSupport;
import com.dyx.market.infrastructure.adapter.repository.ChatCreditSessionSupport;
import com.dyx.market.infrastructure.adapter.repository.ChatRequestIdempotencySupport;
import com.dyx.market.management.config.PlatformConfigService;
import com.dyx.market.trigger.api.dto.ChatbotAskRequestDTO;
import com.dyx.market.types.enums.ResponseCode;
import com.dyx.market.types.exception.AppException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * NR-002/014: 跨用户不能读取缓存；失败重放抛出一致业务码。
 */
@RunWith(MockitoJUnitRunner.class)
public class ChatbotApplicationServiceIdempotencyTest {

    @Mock
    private PlatformConfigService platformConfigService;
    @Mock
    private MarketCreditGatewayClient marketCreditGatewayClient;
    @Mock
    private ChatCreditSessionSupport chatCreditSessionSupport;
    @Mock
    private ChatRequestIdempotencySupport chatRequestIdempotencySupport;
    @Mock
    private ChatTokenUserSupport chatTokenUserSupport;
    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private ChatbotApplicationService chatbotApplicationService;

    @Test
    public void ask_doesNotReturnOtherUsersCachedResponse() {
        when(chatTokenUserSupport.resolveUserId("token-b")).thenReturn("user-b");
        when(chatRequestIdempotencySupport.findCompleted("user-b", "shared-req")).thenReturn(null);
        when(chatRequestIdempotencySupport.tryMarkProcessing("user-b", "shared-req")).thenReturn(false);
        when(chatRequestIdempotencySupport.findCompleted("user-b", "shared-req")).thenReturn(null);

        ChatbotAskRequestDTO request = new ChatbotAskRequestDTO();
        request.setMessage("hi");
        request.setRequestId("shared-req");

        try {
            chatbotApplicationService.ask(request, "token-b");
        } catch (AppException e) {
            assertEquals(ResponseCode.UN_ERROR.getCode(), e.getCode());
        }

        verify(chatRequestIdempotencySupport, never()).findCompleted(eq("user-a"), anyString());
        verify(marketCreditGatewayClient, never()).deductCredit(anyString(), anyInt(), anyString());
    }

    @Test
    public void localFallback_doesNotRequireTokenOrCredit() {
        when(chatRequestIdempotencySupport.findCompleted("__anonymous__", "req-invalid")).thenReturn(null);
        when(chatRequestIdempotencySupport.tryMarkProcessing("__anonymous__", "req-invalid")).thenReturn(false);

        ChatbotAskRequestDTO request = new ChatbotAskRequestDTO();
        request.setMessage("hi");
        request.setRequestId("req-invalid");

        try {
            chatbotApplicationService.ask(request, null);
        } catch (AppException e) {
            assertEquals(ResponseCode.UN_ERROR.getCode(), e.getCode());
        }

        verify(chatRequestIdempotencySupport, times(2)).findCompleted("__anonymous__", "req-invalid");
        verify(chatRequestIdempotencySupport).tryMarkProcessing("__anonymous__", "req-invalid");
        verify(marketCreditGatewayClient, never()).deductCredit(anyString(), anyInt(), anyString());
    }

    @Test
    public void replayFailedRequestThrowsSameErrorCode() {
        when(chatTokenUserSupport.resolveUserId("token-a")).thenReturn("user-a");
        when(chatRequestIdempotencySupport.findCompleted(eq("user-a"), eq("req-fail")))
                .thenReturn(ChatRequestIdempotencySupport.CachedChatResponse.builder()
                        .status("completed")
                        .success(false)
                        .errorCode(ResponseCode.UN_ERROR.getCode())
                        .errorMessage("AI 服务暂时不可用")
                        .answer("AI 服务暂时不可用")
                        .creditDeducted(BigDecimal.ONE)
                        .creditBalance(BigDecimal.TEN)
                        .build());

        try {
            ChatbotAskRequestDTO req = new ChatbotAskRequestDTO();
            req.setMessage("hi");
            req.setRequestId("req-fail");
            chatbotApplicationService.ask(req, "token-a");
        } catch (AppException e) {
            assertEquals(ResponseCode.UN_ERROR.getCode(), e.getCode());
            assertEquals("AI 服务暂时不可用", e.getInfo());
        }
    }
}

package com.dyx.market.chatbot.support;

import com.dyx.market.chatbot.client.MarketCreditGatewayClient;
import com.dyx.market.trigger.api.dto.ChatbotAskResponseDTO;
import com.dyx.market.types.enums.ResponseCode;
import com.dyx.market.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.math.BigDecimal;

/**
 * Chatbot 错误响应构建支持：为 /ask 端点异常补充积分余额与用户友好提示。
 */
@Slf4j
@Component
public class ChatbotErrorResponseSupport {

    @Resource
    private MarketCreditGatewayClient marketCreditGatewayClient;

    /** 根据业务异常码构建 /ask 错误响应体，尽力附带当前积分余额。 */
    public ChatbotAskResponseDTO buildErrorData(AppException e, String token) {
        BigDecimal balance = BigDecimal.ZERO;
        if (StringUtils.isNotBlank(token)) {
            try {
                balance = marketCreditGatewayClient.fetchCreditBalance(token);
            } catch (Exception ex) {
                log.warn("Failed to fetch balance for error response", ex);
            }
        }
        String answer = e.getInfo();
        if (ResponseCode.Login.TOKEN_ERROR.getCode().equals(e.getCode())) {
            answer = "登录已过期，请重新登录后再使用 AI 对话。";
        } else if (ResponseCode.UN_ERROR.getCode().equals(e.getCode())) {
            answer = "AI 服务暂时不可用，已退还本次扣减的积分。请稍后再试。";
        }
        return ChatbotAskResponseDTO.builder()
                .success(false)
                .answer(answer)
                .creditDeducted(BigDecimal.ZERO)
                .creditBalance(balance)
                .build();
    }
}

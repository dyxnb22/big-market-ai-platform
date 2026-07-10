package com.dyx.market.trigger.application;

import com.dyx.market.domain.credit.model.entity.TradeEntity;
import com.dyx.market.domain.credit.model.valobj.TradeNameVO;
import com.dyx.market.domain.credit.model.valobj.TradeTypeVO;
import com.dyx.market.domain.chat.adapter.repository.IChatCreditSessionRepository;
import com.dyx.market.trigger.adapter.IAccountCreditWriteAdapter;
import com.dyx.market.trigger.adapter.IAccountReadAdapter;
import com.dyx.market.types.enums.ResponseCode;
import com.dyx.market.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;

/**
 * AI Chat 积分扣减/退还应用服务。
 */
@Slf4j
@Service
public class ChatCreditApplicationService {

    @Resource
    private IAccountCreditWriteAdapter accountCreditWriteAdapter;
    @Resource
    private IAccountReadAdapter accountRemoteReadAdapter;
    @Resource
    private IChatCreditSessionRepository chatCreditSessionRepository;

    public BigDecimal deduct(String userId, int amount, String requestId) {
        validate(userId, requestId, amount);
        try {
            String orderId = accountCreditWriteAdapter.createOrder(TradeEntity.builder()
                    .userId(userId)
                    .tradeName(TradeNameVO.OPENAI_PAY)
                    .tradeType(TradeTypeVO.REVERSE)
                    .amount(BigDecimal.valueOf(amount).negate())
                    .outBusinessNo("chat_" + requestId)
                    .build());
            chatCreditSessionRepository.recordDeduction(userId, requestId, amount);
            log.info("AI Chat积分扣减完成 userId:{} amount:{} orderId:{}", userId, amount, orderId);
            return queryCreditBalanceSafe(userId);
        } catch (AppException e) {
            if (ResponseCode.INDEX_DUP.getCode().equals(e.getCode())) {
                log.warn("AI Chat积分扣减重复 userId:{} requestId:{}", userId, requestId);
                chatCreditSessionRepository.recordDeduction(userId, requestId, amount);
                return queryCreditBalanceSafe(userId);
            }
            throw e;
        }
    }

    public BigDecimal refund(String userId, int amount, String originalRequestId) {
        validate(userId, originalRequestId, amount);
        try {
            String orderId = accountCreditWriteAdapter.createOrder(TradeEntity.builder()
                    .userId(userId)
                    .tradeName(TradeNameVO.OPENAI_PAY)
                    .tradeType(TradeTypeVO.FORWARD)
                    .amount(BigDecimal.valueOf(amount))
                    .outBusinessNo("chat_refund_" + originalRequestId)
                    .build());
            chatCreditSessionRepository.updateRefundState(originalRequestId, "refunded");
            log.info("AI Chat积分退还完成 userId:{} amount:{} orderId:{}", userId, amount, orderId);
            return queryCreditBalanceSafe(userId);
        } catch (AppException e) {
            if (ResponseCode.INDEX_DUP.getCode().equals(e.getCode())) {
                log.warn("AI Chat积分退还重复 userId:{} requestId:{}", userId, originalRequestId);
                chatCreditSessionRepository.updateRefundState(originalRequestId, "refunded");
                return queryCreditBalanceSafe(userId);
            }
            chatCreditSessionRepository.markRefundPending(userId, originalRequestId, amount);
            throw e;
        } catch (Exception e) {
            chatCreditSessionRepository.markRefundPending(userId, originalRequestId, amount);
            throw e;
        }
    }

    public void markRefundPending(String userId, String requestId, int amount) {
        chatCreditSessionRepository.markRefundPending(userId, requestId, amount);
    }

    private static void validate(String userId, String requestId, int amount) {
        if (StringUtils.isBlank(userId) || StringUtils.isBlank(requestId) || amount <= 0) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), ResponseCode.ILLEGAL_PARAMETER.getInfo());
        }
    }

    private BigDecimal queryCreditBalanceSafe(String userId) {
        try {
            return accountRemoteReadAdapter.queryUserCreditAccount(userId);
        } catch (Exception e) {
            log.warn("查询用户积分失败 userId:{}", userId, e);
            return BigDecimal.ZERO;
        }
    }
}

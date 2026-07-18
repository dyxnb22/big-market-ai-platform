package com.dyx.market.trigger.application;

import com.dyx.market.domain.chat.adapter.repository.IChatCreditSessionRepository;
import com.dyx.market.domain.chat.model.ChatCreditSessionSnapshot;
import com.dyx.market.domain.credit.model.entity.TradeEntity;
import com.dyx.market.domain.credit.model.valobj.TradeNameVO;
import com.dyx.market.domain.credit.model.valobj.TradeTypeVO;
import com.dyx.market.trigger.adapter.IAccountCreditWriteAdapter;
import com.dyx.market.trigger.adapter.IAccountReadAdapter;
import com.dyx.market.types.enums.ResponseCode;
import com.dyx.market.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
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
        // Durable intent first — enables refund/reconcile even if session write after RPC would fail.
        chatCreditSessionRepository.recordDeductingIntent(userId, requestId, amount);
        try {
            String orderId = accountCreditWriteAdapter.createOrder(TradeEntity.builder()
                    .userId(userId)
                    .tradeName(TradeNameVO.OPENAI_PAY)
                    .tradeType(TradeTypeVO.REVERSE)
                    .amount(BigDecimal.valueOf(amount).negate())
                    .outBusinessNo(chatOutBusinessNo(userId, requestId))
                    .build());
            chatCreditSessionRepository.markDeducted(userId, requestId);
            log.info("AI Chat积分扣减完成 userId:{} amount:{} orderId:{}", userId, amount, orderId);
            return queryCreditBalanceSafe(userId);
        } catch (AppException e) {
            if (ResponseCode.INDEX_DUP.getCode().equals(e.getCode())) {
                log.warn("AI Chat积分扣减重复 userId:{} requestId:{}", userId, requestId);
                chatCreditSessionRepository.markDeducted(userId, requestId);
                return queryCreditBalanceSafe(userId);
            }
            // Explicit business rejection: clear intent. UNKNOWN/pending paths keep deducting.
            if (isExplicitReject(e)) {
                chatCreditSessionRepository.markDeductFailed(userId, requestId);
            }
            throw e;
        }
    }

    private static boolean isExplicitReject(AppException e) {
        if (e == null || e.getCode() == null) {
            return false;
        }
        String code = e.getCode();
        return ResponseCode.ILLEGAL_PARAMETER.getCode().equals(code)
                || ResponseCode.USER_CREDIT_ACCOUNT_NO_AVAILABLE_AMOUNT.getCode().equals(code);
    }

    /**
     * 按扣费会话退款：金额取自 session，拒绝无 session 或已退款的伪造请求。
     */
    public BigDecimal refund(String userId, String originalRequestId) {
        if (StringUtils.isBlank(userId) || StringUtils.isBlank(originalRequestId)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), ResponseCode.ILLEGAL_PARAMETER.getInfo());
        }
        ChatCreditSessionSnapshot session = chatCreditSessionRepository.findSession(userId, originalRequestId);
        if (session == null || !session.isDeducted() || session.getDeductAmount() <= 0) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "未找到可退款的扣费会话");
        }
        if (IChatCreditSessionRepository.REFUND_REFUNDED.equals(session.getRefundState())) {
            return queryCreditBalanceSafe(userId);
        }
        if (!chatCreditSessionRepository.tryBeginRefund(userId, originalRequestId)) {
            ChatCreditSessionSnapshot latest = chatCreditSessionRepository.findSession(userId, originalRequestId);
            if (latest != null && IChatCreditSessionRepository.REFUND_REFUNDED.equals(latest.getRefundState())) {
                return queryCreditBalanceSafe(userId);
            }
            throw new AppException(ResponseCode.UN_ERROR.getCode(), "退款处理中或状态不可退款");
        }
        int amount = session.getDeductAmount();
        try {
            String orderId = accountCreditWriteAdapter.createOrder(TradeEntity.builder()
                    .userId(userId)
                    .tradeName(TradeNameVO.OPENAI_PAY)
                    .tradeType(TradeTypeVO.FORWARD)
                    .amount(BigDecimal.valueOf(amount))
                    .outBusinessNo(chatRefundOutBusinessNo(userId, originalRequestId))
                    .build());
            chatCreditSessionRepository.updateRefundState(userId, originalRequestId,
                    IChatCreditSessionRepository.REFUND_REFUNDED);
            log.info("AI Chat积分退还完成 userId:{} amount:{} orderId:{}", userId, amount, orderId);
            return queryCreditBalanceSafe(userId);
        } catch (AppException e) {
            if (ResponseCode.INDEX_DUP.getCode().equals(e.getCode())) {
                log.warn("AI Chat积分退还重复 userId:{} requestId:{}", userId, originalRequestId);
                chatCreditSessionRepository.updateRefundState(userId, originalRequestId,
                        IChatCreditSessionRepository.REFUND_REFUNDED);
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
        if (StringUtils.isBlank(userId) || StringUtils.isBlank(requestId)) {
            return;
        }
        ChatCreditSessionSnapshot session = chatCreditSessionRepository.findSession(userId, requestId);
        if (session == null || !session.isDeducted()) {
            return;
        }
        int effectiveAmount = session.getDeductAmount() > 0 ? session.getDeductAmount() : amount;
        if (effectiveAmount <= 0) {
            return;
        }
        chatCreditSessionRepository.markRefundPending(userId, requestId, effectiveAmount);
    }

    static String chatOutBusinessNo(String userId, String requestId) {
        return "chat_" + userId + "_" + requestId;
    }

    static String chatRefundOutBusinessNo(String userId, String requestId) {
        return "chat_refund_" + userId + "_" + requestId;
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

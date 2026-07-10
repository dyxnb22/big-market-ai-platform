package com.dyx.market.infrastructure.adapter.repository;

import com.dyx.market.infrastructure.dao.IChatCreditSessionDao;
import com.dyx.market.infrastructure.dao.po.ChatCreditSession;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * Chatbot 扣款/退款会话持久化，供退款补偿 Job 扫描。
 */
@Slf4j
@Component
public class ChatCreditSessionSupport {

    public static final String REFUND_NONE = "none";
    public static final String REFUND_PENDING = "pending";
    public static final String REFUND_REFUNDED = "refunded";

    @Resource
    private IChatCreditSessionDao chatCreditSessionDao;

    public void recordDeduction(String userId, String requestId, int amount) {
        if (StringUtils.isBlank(userId) || StringUtils.isBlank(requestId) || amount <= 0) {
            return;
        }
        try {
            chatCreditSessionDao.insert(ChatCreditSession.builder()
                    .userId(userId)
                    .requestId(requestId)
                    .deducted(true)
                    .deductAmount(amount)
                    .refundState(REFUND_NONE)
                    .retryCount(0)
                    .build());
        } catch (DuplicateKeyException e) {
            log.debug("[ChatCreditSession] deduction already recorded requestId:{}", requestId);
        }
    }

    public void markRefunded(String requestId) {
        if (StringUtils.isBlank(requestId)) {
            return;
        }
        chatCreditSessionDao.updateRefundState(ChatCreditSession.builder()
                .requestId(requestId)
                .refundState(REFUND_REFUNDED)
                .build());
    }

    public void markRefundPending(String requestId) {
        if (StringUtils.isBlank(requestId)) {
            return;
        }
        chatCreditSessionDao.updateRefundState(ChatCreditSession.builder()
                .requestId(requestId)
                .refundState(REFUND_PENDING)
                .build());
    }
}

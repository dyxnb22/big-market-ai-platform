package com.dyx.market.infrastructure.adapter.repository;

import com.dyx.market.infrastructure.dao.IChatCreditSessionDao;
import com.dyx.market.infrastructure.dao.po.ChatCreditSession;
import com.dyx.market.middleware.db.router.DBRouterTemplate;
import com.dyx.market.middleware.db.router.strategy.IDBRouterStrategy;
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

    @Resource
    private IDBRouterStrategy dbRouter;

    public void recordDeduction(String userId, String requestId, int amount) {
        if (StringUtils.isBlank(userId) || StringUtils.isBlank(requestId) || amount <= 0) {
            return;
        }
        DBRouterTemplate.executeOnShard(dbRouter, userId, () -> {
            try {
                chatCreditSessionDao.insert(ChatCreditSession.builder()
                        .userId(userId)
                        .requestId(requestId)
                        .deducted(true)
                        .deductAmount(amount)
                        .deductState("deducted")
                        .refundState(REFUND_NONE)
                        .retryCount(0)
                        .build());
            } catch (DuplicateKeyException e) {
                log.debug("[ChatCreditSession] deduction already recorded requestId:{}", requestId);
            }
        });
    }

    public void markRefunded(String userId, String requestId) {
        updateRefundState(userId, requestId, REFUND_REFUNDED);
    }

    public void markRefundPending(String userId, String requestId) {
        updateRefundState(userId, requestId, REFUND_PENDING);
    }

    private void updateRefundState(String userId, String requestId, String refundState) {
        if (StringUtils.isBlank(userId) || StringUtils.isBlank(requestId)) {
            return;
        }
        DBRouterTemplate.executeOnShard(dbRouter, userId, () -> {
            int affected = chatCreditSessionDao.updateRefundState(ChatCreditSession.builder()
                    .userId(userId)
                    .requestId(requestId)
                    .refundState(refundState)
                    .build());
            if (affected == 0) {
                log.warn("[ChatCreditSession] refund state not updated userId:{} requestId:{} state:{}",
                        userId, requestId, refundState);
            }
        });
    }
}

package com.dyx.market.domain.chat.adapter.repository;

import com.dyx.market.domain.chat.model.ChatCreditSessionSnapshot;

/**
 * Chatbot 积分扣款/退款会话仓储（G-07 outbox）。
 */
public interface IChatCreditSessionRepository {

    String REFUND_NONE = "none";
    String REFUND_REFUNDING = "refunding";
    String REFUND_REFUNDED = "refunded";
    String REFUND_PENDING = "pending";

    void recordDeduction(String userId, String requestId, int amount);

    ChatCreditSessionSnapshot findSession(String userId, String requestId);

    /**
     * CAS：refund_state none|pending → refunding，仅当已扣费且归属用户匹配时成功。
     */
    boolean tryBeginRefund(String userId, String requestId);

    void updateRefundState(String userId, String requestId, String refundState);

    void markRefundPending(String userId, String requestId, int amount);
}

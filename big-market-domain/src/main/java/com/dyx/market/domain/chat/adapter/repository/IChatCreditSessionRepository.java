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

    String DEDUCT_DEDUCTING = "deducting";
    String DEDUCT_DEDUCTED = "deducted";
    String DEDUCT_FAILED = "failed";

    /** Insert durable deduct intent before remote debit (deduct_state=deducting). */
    void recordDeductingIntent(String userId, String requestId, int amount);

    /** CAS / mark session as remotely deducted (refundable). */
    void markDeducted(String userId, String requestId);

    /** Mark intent failed only when remote debit is known not to have happened. */
    void markDeductFailed(String userId, String requestId);

    /** Legacy/compat: insert as already deducted (chatbot INDEX_DUP recovery). */
    void recordDeduction(String userId, String requestId, int amount);

    ChatCreditSessionSnapshot findSession(String userId, String requestId);

    /**
     * CAS：refund_state none|pending → refunding，仅当已扣费且归属用户匹配时成功。
     */
    boolean tryBeginRefund(String userId, String requestId);

    void updateRefundState(String userId, String requestId, String refundState);

    void markRefundPending(String userId, String requestId, int amount);
}

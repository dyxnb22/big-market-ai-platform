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

    /** 在远程扣款前持久化扣费意图（deduct_state=deducting），为 UNKNOWN 结果保留退款/对账依据。 */
    void recordDeductingIntent(String userId, String requestId, int amount);

    /** 通过 CAS 将会话标记为远程扣款成功（deduct_state=deducted），使其具备退款依据。 */
    void markDeducted(String userId, String requestId);

    /** 仅在能够确认远程扣款没有发生时，才将扣费意图标记为 failed。 */
    void markDeductFailed(String userId, String requestId);

    /** 兼容旧路径：直接插入已扣款会话，用于 chatbot 收到 INDEX_DUP 后恢复状态。 */
    void recordDeduction(String userId, String requestId, int amount);

    ChatCreditSessionSnapshot findSession(String userId, String requestId);

    /**
     * CAS：refund_state none|pending → refunding，仅当已扣费且归属用户匹配时成功。
     */
    boolean tryBeginRefund(String userId, String requestId);

    void updateRefundState(String userId, String requestId, String refundState);

    void markRefundPending(String userId, String requestId, int amount);
}

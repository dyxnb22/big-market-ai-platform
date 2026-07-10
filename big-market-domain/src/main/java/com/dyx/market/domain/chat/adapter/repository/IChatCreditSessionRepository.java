package com.dyx.market.domain.chat.adapter.repository;

/**
 * Chatbot 积分扣款/退款会话仓储（G-07 outbox）。
 */
public interface IChatCreditSessionRepository {

    void recordDeduction(String userId, String requestId, int amount);

    void updateRefundState(String requestId, String refundState);

    void markRefundPending(String userId, String requestId, int amount);
}

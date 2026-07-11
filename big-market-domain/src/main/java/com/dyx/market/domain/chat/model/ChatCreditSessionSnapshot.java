package com.dyx.market.domain.chat.model;

import lombok.Builder;
import lombok.Value;

/**
 * Chat 扣费会话只读快照（退款校验用）。
 */
@Value
@Builder
public class ChatCreditSessionSnapshot {

    String userId;
    String requestId;
    int deductAmount;
    boolean deducted;
    String deductState;
    String refundState;
}

package com.dyx.market.infrastructure.dao.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * Chat 积分扣费会话持久化对象。
 *
 * <p>该表先记录扣费意图，再调用 account-service 执行远程交易；扣费和退款状态用于
 * 处理超时、进程崩溃及重复请求，确保未知结果不会被误判为失败。</p>
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ChatCreditSession {

    /** 自增主键。 */
    private Long id;
    /** 用户 ID，也是分库路由键。 */
    private String userId;
    /** Chat 请求幂等 ID。 */
    private String requestId;
    /** 兼容旧字段；true 表示远程扣费已确认并允许退款。 */
    private Boolean deducted;
    /** 实际扣减积分数量。 */
    private Integer deductAmount;
    /** 扣费状态：deducting-待确认、deducted-已扣费、failed-明确失败、manual_pending-人工处理。 */
    private String deductState;
    /** 退款状态：none-未退款、pending-待补偿、refunding-退款中、refunded-已退款、manual_pending-人工处理。 */
    private String refundState;
    /** 已执行的对账/退款重试次数。 */
    private Integer retryCount;
    /** 最近一次失败原因，通常已截断以避免保存过大内容。 */
    private String lastError;
    /** 下一次允许执行补偿或对账的时间。 */
    private Date nextRetryTime;
    /** 创建时间。 */
    private Date createTime;
    /** 最近更新时间。 */
    private Date updateTime;
}

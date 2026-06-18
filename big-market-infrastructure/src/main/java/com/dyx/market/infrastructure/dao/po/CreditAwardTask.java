package com.dyx.market.infrastructure.dao.po;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 积分发奖 Outbox 行（默认实现）。
 * <p>
 * 在 {@code account.award-credit-outbox.enabled=true} 时，与 {@code updateAwardRecordCompletedState}
 * 同事务插入；轮询 Job 读取 pending 行并调用 {@code IAccountCreditWriteAdapter.createOrder()}，
 * 以 {@code awardOrderId} 作为 {@code outBusinessNo} 保证幂等。
 * <p>
 * 表：{@code credit_award_task_000..003}（与用户中奖记录同分片）。
 */
@Data
public class CreditAwardTask {

    /** 自增主键 */
    private Long id;
    /** 用户 ID（分片键，与 user_award_record 一致） */
    private String userId;
    /** 幂等键：UserAwardRecordEntity 的 orderId，每次发奖唯一 */
    private String awardOrderId;
    /** 待发放积分数量 */
    private BigDecimal creditAmount;
    /** 状态：pending | dispatched | failed */
    private String state;
    /** 派发失败重试次数 */
    private Integer retryCount;
    /** 创建时间 */
    private Date createTime;
    /** 最后更新时间 */
    private Date updateTime;

}

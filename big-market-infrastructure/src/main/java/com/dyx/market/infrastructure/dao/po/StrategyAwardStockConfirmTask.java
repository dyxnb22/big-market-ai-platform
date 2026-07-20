package com.dyx.market.infrastructure.dao.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
/**
 * 奖品库存确认 Outbox 任务。
 *
 * <p>抽奖结果落库后再确认预占库存；任务状态允许 Job 在进程崩溃或远程失败后继续重试。</p>
 */
public class StrategyAwardStockConfirmTask {

    /** 数据库主键。 */
    private Long id;
    /** 发起抽奖的用户。 */
    private String userId;
    /** 抽奖参与订单号。 */
    private String orderId;
    /** 策略 ID。 */
    private Long strategyId;
    /** 奖品 ID。 */
    private Integer awardId;
    /** Redis/库存预占的业务幂等号。 */
    private String reservationId;
    /** 本次预占仍需确认的库存数量。 */
    private Long lockSurplus;
    /** pending、processing、confirmed、failed 等任务状态。 */
    private String state;
    /** Job 重试次数。 */
    private Integer retryCount;
    private Date createTime;
    private Date updateTime;
}

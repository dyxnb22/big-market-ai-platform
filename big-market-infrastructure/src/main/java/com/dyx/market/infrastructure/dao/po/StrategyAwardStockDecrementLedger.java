package com.dyx.market.infrastructure.dao.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 策略奖品库存 MySQL 扣减幂等账本：{@code strategy_award_stock_decrement_ledger}。
 * 唯一键 {@code reservation_id} 保证每个预占只扣一次。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class StrategyAwardStockDecrementLedger {

    /** 自增主键。 */
    private Long id;
    /** 奖品库存预占幂等 ID；唯一键保证同一预占只扣一次。 */
    private String reservationId;
    /** 抽奖策略 ID。 */
    private Long strategyId;
    /** 奖品 ID。 */
    private Integer awardId;
    /** 本次预占对应的 Redis 剩余库存值。 */
    private Long lockSurplus;
    /** 账本状态，例如 pending-待确认、applied-已落库、failed-失败。 */
    private String status;
    /** 创建时间。 */
    private Date createTime;
    /** 最近更新时间。 */
    private Date updateTime;
}

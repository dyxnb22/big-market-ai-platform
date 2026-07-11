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

    private Long id;
    private String reservationId;
    private Long strategyId;
    private Integer awardId;
    private Long lockSurplus;
    private String status;
    private Date createTime;
    private Date updateTime;
}

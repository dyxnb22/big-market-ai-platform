package com.dyx.market.domain.strategy.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 奖品库存确认补偿任务。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StrategyAwardStockConfirmTaskEntity {

    private String userId;
    private String orderId;
    private Long strategyId;
    private Integer awardId;
    private String reservationId;
    private Long lockSurplus;
    private String state;
    private Integer retryCount;
}

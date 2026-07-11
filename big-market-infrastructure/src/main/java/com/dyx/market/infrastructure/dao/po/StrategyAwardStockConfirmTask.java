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
public class StrategyAwardStockConfirmTask {

    private Long id;
    private String userId;
    private String orderId;
    private Long strategyId;
    private Integer awardId;
    private String reservationId;
    private Long lockSurplus;
    private String state;
    private Integer retryCount;
    private Date createTime;
    private Date updateTime;
}

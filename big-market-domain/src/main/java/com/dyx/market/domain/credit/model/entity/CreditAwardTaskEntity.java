package com.dyx.market.domain.credit.model.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 积分发奖 Outbox 任务投影，供派发任务 Job 边界使用。
 */
@Data
public class CreditAwardTaskEntity {

    private Long id;
    private String userId;
    private String awardOrderId;
    private BigDecimal creditAmount;
    private String state;
    private Integer retryCount;
    private Date createTime;
    private Date updateTime;

}

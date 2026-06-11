package com.dyx.market.domain.credit.model.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * Credit-award outbox task projection used by the dispatch job boundary.
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

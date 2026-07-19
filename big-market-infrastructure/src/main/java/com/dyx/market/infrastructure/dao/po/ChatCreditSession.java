package com.dyx.market.infrastructure.dao.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ChatCreditSession {

    private Long id;
    private String userId;
    private String requestId;
    private Boolean deducted;
    private Integer deductAmount;
    /** deducting | deducted | failed | manual_pending */
    private String deductState;
    /** none | pending | refunding | refunded | manual_pending */
    private String refundState;
    private Integer retryCount;
    private String lastError;
    private Date nextRetryTime;
    private Date createTime;
    private Date updateTime;
}

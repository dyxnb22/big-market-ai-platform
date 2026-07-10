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
public class MqDeadLetter {

    private Long id;
    private String messageId;
    private String businessMessageId;
    private String queue;
    private String payload;
    private String state;
    private Integer retryCount;
    private Integer consumeFailCount;
    private Date createTime;
    private Date updateTime;
}

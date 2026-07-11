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
public class PendingRemoteWriteTask {

    private Long id;
    private String outBusinessNo;
    private String operation;
    private String payload;
    private String state;
    private Integer retryCount;
    private Date createTime;
    private Date updateTime;
}

package com.dyx.market.infrastructure.dao.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/** Durable idempotency record for a SKU stock release. */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ActivitySkuStockRestoreLedger {
    private Long id;
    private Long sku;
    private String reservationId;
    private String status;
    private Date createTime;
    private Date updateTime;
}

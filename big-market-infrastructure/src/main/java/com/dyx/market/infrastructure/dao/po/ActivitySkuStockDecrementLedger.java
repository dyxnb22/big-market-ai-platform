package com.dyx.market.infrastructure.dao.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 活动 SKU 库存 MySQL 扣减幂等账本：{@code activity_sku_stock_decrement_ledger}。
 * 唯一键 {@code (sku, lock_surplus)} 对应每次 Redis DECR。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ActivitySkuStockDecrementLedger {

    private Long id;
    private Long sku;
    private Long activityId;
    private Long lockSurplus;
    private String status;
    private Date createTime;
    private Date updateTime;
}

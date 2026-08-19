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

    /** 自增主键。 */
    private Long id;
    /** 活动 SKU。 */
    private Long sku;
    /** 活动 ID。 */
    private Long activityId;
    /** 本次 Redis 扣减前对应的剩余库存值；与 SKU 组成扣减唯一键。 */
    private Long lockSurplus;
    /** 兑换或补偿流程使用的库存预占幂等 ID。 */
    private String reservationId;
    /** 账本状态，例如 pending-待确认、applied-已落库、failed-失败。 */
    private String status;
    /** 创建时间。 */
    private Date createTime;
    /** 最近更新时间。 */
    private Date updateTime;
}

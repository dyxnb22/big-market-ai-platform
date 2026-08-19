package com.dyx.market.infrastructure.dao.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/** SKU 库存恢复的持久化幂等记录；唯一键保证同一预占不会重复恢复。 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ActivitySkuStockRestoreLedger {
    /** 自增主键。 */
    private Long id;
    /** 待恢复库存的 SKU。 */
    private Long sku;
    /** 原库存预占幂等 ID。 */
    private String reservationId;
    /** 恢复状态，例如 pending-待处理、restored-已恢复。 */
    private String status;
    /** 创建时间。 */
    private Date createTime;
    /** 最近更新时间。 */
    private Date updateTime;
}

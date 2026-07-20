package com.dyx.market.infrastructure.dao;

import com.dyx.market.infrastructure.dao.po.ActivitySkuStockDecrementLedger;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
/** 活动 SKU 库存扣减账本 DAO，支撑预占、刷库、释放和补偿。 */
public interface IActivitySkuStockDecrementLedgerDao {

    /** 写入一次 SKU 库存预占。 */
    void insert(ActivitySkuStockDecrementLedger ledger);

    /** 查询指定 SKU 与锁定数量对应的预占记录。 */
    ActivitySkuStockDecrementLedger queryBySkuAndLockSurplus(ActivitySkuStockDecrementLedger query);

    /** 按 reservationId 查询预占记录。 */
    ActivitySkuStockDecrementLedger queryByReservationId(String reservationId);

    /** 删除尚未刷入数据库的预占记录。 */
    int deleteBySkuAndLockSurplus(ActivitySkuStockDecrementLedger query);

    /** 将预占标记为已应用到持久化库存。 */
    int updateStatusApplied(ActivitySkuStockDecrementLedger query);

    /** 将预占标记为已释放。 */
    int updateStatusReleased(ActivitySkuStockDecrementLedger query);

    /** 查询指定 SKU 尚未完成应用/释放的预占记录。 */
    List<ActivitySkuStockDecrementLedger> queryReservedBySku(Long sku);
}

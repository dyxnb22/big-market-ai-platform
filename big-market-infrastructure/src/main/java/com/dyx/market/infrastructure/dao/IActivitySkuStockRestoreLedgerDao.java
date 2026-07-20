package com.dyx.market.infrastructure.dao;

import com.dyx.market.infrastructure.dao.po.ActivitySkuStockRestoreLedger;
import org.apache.ibatis.annotations.Mapper;

@Mapper
/**
 * 活动 SKU 库存恢复账本 DAO。
 *
 * <p>恢复账本以 reservationId 保证幂等：明确拒绝时移除未应用扣减，
 * 已应用扣减则只允许一次恢复。</p>
 */
public interface IActivitySkuStockRestoreLedgerDao {
    /** 插入一次 SKU 库存恢复意图。重复 reservationId 由唯一键拦截。 */
    int insert(ActivitySkuStockRestoreLedger ledger);

    /** 按库存预占号查询恢复账本。 */
    ActivitySkuStockRestoreLedger queryByReservationId(String reservationId);

    /** 将恢复账本从待处理推进为已应用，返回实际更新行数。 */
    int updateStatusApplied(ActivitySkuStockRestoreLedger ledger);
}

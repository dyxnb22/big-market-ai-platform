package com.dyx.market.infrastructure.dao;

import com.dyx.market.infrastructure.dao.po.ActivitySkuStockDecrementLedger;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface IActivitySkuStockDecrementLedgerDao {

    void insert(ActivitySkuStockDecrementLedger ledger);

    ActivitySkuStockDecrementLedger queryBySkuAndLockSurplus(ActivitySkuStockDecrementLedger query);

    ActivitySkuStockDecrementLedger queryByReservationId(String reservationId);

    int deleteBySkuAndLockSurplus(ActivitySkuStockDecrementLedger query);

    int updateStatusApplied(ActivitySkuStockDecrementLedger query);

    int updateStatusReleased(ActivitySkuStockDecrementLedger query);

    List<ActivitySkuStockDecrementLedger> queryReservedBySku(Long sku);
}

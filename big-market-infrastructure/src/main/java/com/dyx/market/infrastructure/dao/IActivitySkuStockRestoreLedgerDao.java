package com.dyx.market.infrastructure.dao;

import com.dyx.market.infrastructure.dao.po.ActivitySkuStockRestoreLedger;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface IActivitySkuStockRestoreLedgerDao {
    int insert(ActivitySkuStockRestoreLedger ledger);
    ActivitySkuStockRestoreLedger queryByReservationId(String reservationId);
    int updateStatusApplied(ActivitySkuStockRestoreLedger ledger);
}

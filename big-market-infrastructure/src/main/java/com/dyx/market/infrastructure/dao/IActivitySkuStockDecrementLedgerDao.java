package com.dyx.market.infrastructure.dao;

import com.dyx.market.infrastructure.dao.po.ActivitySkuStockDecrementLedger;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface IActivitySkuStockDecrementLedgerDao {

    void insert(ActivitySkuStockDecrementLedger ledger);

    ActivitySkuStockDecrementLedger queryBySkuAndLockSurplus(ActivitySkuStockDecrementLedger query);
}

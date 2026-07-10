package com.dyx.market.infrastructure.dao;

import com.dyx.market.infrastructure.dao.po.StrategyAwardStockDecrementLedger;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface IStrategyAwardStockDecrementLedgerDao {

    void insert(StrategyAwardStockDecrementLedger ledger);

    StrategyAwardStockDecrementLedger queryByReservationId(String reservationId);
}

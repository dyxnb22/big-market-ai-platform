package com.dyx.market.infrastructure.dao;

import com.dyx.market.infrastructure.dao.po.StrategyAwardStockDecrementLedger;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface IStrategyAwardStockDecrementLedgerDao {

    void insert(StrategyAwardStockDecrementLedger ledger);

    StrategyAwardStockDecrementLedger queryByReservationId(String reservationId);

    List<StrategyAwardStockDecrementLedger> queryReservedByStrategyAward(@Param("strategyId") Long strategyId,
                                                                          @Param("awardId") Integer awardId);

    List<StrategyAwardStockDecrementLedger> queryAllReserved(@Param("limit") int limit);

    int updateLockSurplus(StrategyAwardStockDecrementLedger ledger);

    int updateStatusApplied(String reservationId);

    int updateStatusReleased(String reservationId);
}

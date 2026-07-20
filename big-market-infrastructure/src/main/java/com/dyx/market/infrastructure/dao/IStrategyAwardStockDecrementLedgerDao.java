package com.dyx.market.infrastructure.dao;

import com.dyx.market.infrastructure.dao.po.StrategyAwardStockDecrementLedger;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
/** 奖品库存预占/释放账本 DAO，记录 Redis 预扣与 MySQL 持久化之间的过渡状态。 */
public interface IStrategyAwardStockDecrementLedgerDao {

    /** 新建一条库存预占账本。 */
    void insert(StrategyAwardStockDecrementLedger ledger);

    /** 按 reservationId 查询预占账本，用于确认或释放。 */
    StrategyAwardStockDecrementLedger queryByReservationId(String reservationId);

    /** 查询指定奖品仍处于 reserved 状态的账本。 */
    List<StrategyAwardStockDecrementLedger> queryReservedByStrategyAward(@Param("strategyId") Long strategyId,
                                                                          @Param("awardId") Integer awardId);

    /** 查询所有待同步的库存预占，供补偿任务限量扫描。 */
    List<StrategyAwardStockDecrementLedger> queryAllReserved(@Param("limit") int limit);

    /** 原子更新账本中的剩余锁定库存。 */
    int updateLockSurplus(StrategyAwardStockDecrementLedger ledger);

    /** 将预占标记为已应用。 */
    int updateStatusApplied(String reservationId);

    /** 将预占标记为已释放。 */
    int updateStatusReleased(String reservationId);
}

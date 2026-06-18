package com.dyx.market.infrastructure.dao;

import com.dyx.market.infrastructure.dao.po.RaffleQuotaDecrementLedger;
import com.dyx.market.middleware.db.router.annotation.DBRouter;
import com.dyx.market.middleware.db.router.annotation.DBRouterStrategy;
import org.apache.ibatis.annotations.Mapper;

/**
 * 活动配额扣减幂等账本 {@code raffle_quota_decrement_ledger_{000..003}} 的 DAO。
 * <p>
 * INSERT 与配额扣减在同一 {@code transactionTemplate} 事务内执行；
 * 若因 {@code outBusinessNo} 重复触发 {@code DuplicateKeyException}，表示该次扣减已生效。
 */
@Mapper
@DBRouterStrategy(splitTable = true)
public interface IRaffleQuotaDecrementLedgerDao {

    @DBRouter
    void insert(RaffleQuotaDecrementLedger ledger);

    @DBRouter
    RaffleQuotaDecrementLedger queryByKey(RaffleQuotaDecrementLedger query);

    @DBRouter
    int updateStatusToRolledBack(RaffleQuotaDecrementLedger query);

}

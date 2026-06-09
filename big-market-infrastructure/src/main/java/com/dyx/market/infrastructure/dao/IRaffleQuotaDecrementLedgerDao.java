package com.dyx.market.infrastructure.dao;

import com.dyx.market.infrastructure.dao.po.RaffleQuotaDecrementLedger;
import com.dyx.market.middleware.db.router.annotation.DBRouterStrategy;
import org.apache.ibatis.annotations.Mapper;

/**
 * DAO for raffle_quota_decrement_ledger_{000..003}.
 *
 * Phase 2.2-B12: idempotency ledger for AccountQuotaServiceRPC.decrementQuota.
 * The INSERT is issued inside the same transactionTemplate block as the quota
 * decrement. A DuplicateKeyException on insert means the decrement was already
 * applied for this outBusinessNo.
 */
@Mapper
@DBRouterStrategy(splitTable = true)
public interface IRaffleQuotaDecrementLedgerDao {

    void insert(RaffleQuotaDecrementLedger ledger);

    RaffleQuotaDecrementLedger queryByKey(RaffleQuotaDecrementLedger query);

    int updateStatusToRolledBack(RaffleQuotaDecrementLedger query);

}

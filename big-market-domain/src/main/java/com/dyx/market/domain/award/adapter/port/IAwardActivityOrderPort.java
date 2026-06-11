package com.dyx.market.domain.award.adapter.port;

/**
 * Domain port isolating AwardRepository from direct activity-order DAO access.
 *
 * Phase 7-A prep (AL-5): AwardRepository must not import IUserRaffleOrderDao.
 * The user_raffle_order table is owned by activity-service; fulfillment only
 * needs the guarded create -> used state transition by userId and orderId.
 *
 * Local path (default): LocalAwardActivityOrderPort delegates directly to
 * IUserRaffleOrderDao.updateUserRaffleOrderStateUsed. The caller keeps the
 * existing shard routing and transaction boundary.
 *
 * Remote path (future, flag-gated): activity-service API can replace the local
 * implementation once activity-service owns draw/order writes.
 */
public interface IAwardActivityOrderPort {

    /**
     * Mark the raffle order used if it is still in create state.
     *
     * @param userId  user identifier (shard key; routing is owned by caller)
     * @param orderId raffle order id
     * @return affected row count; same semantics as IUserRaffleOrderDao
     */
    int markUserRaffleOrderUsed(String userId, String orderId);

}

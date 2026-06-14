package com.dyx.market.domain.activity.adapter.port;

import java.math.BigDecimal;

/**
 * Domain port for synchronous, idempotent quota decrement on activity accounts.
 *
 * contract.
 *
 * Design rationale:
 *   decrementQuota is a pre-draw gate and MUST return synchronously so that
 *   RaffleActivityPartakeService can reject the draw immediately on quota exhaustion.
 *   It must NOT be a purely async outbox like creditAward.
 *
 *   Idempotency: callers supply outBusinessNo (the raffle order ID) as the
 *   idempotency key. Repeated calls with the same key are safe — the second call
 *   returns true without double-decrementing.
 *
 *   Saga compensation: rollbackQuota restores a previously decremented slot when
 *   the downstream raffle fails after the quota was taken. It is safe to call even
 *   if the matching decrement was never applied.
 *
 * Local path (default, flag=false):
 *   LocalActivityAccountPort — no-op because RaffleActivityPartakeService still
 *   calls IActivityRepository.saveCreatePartakeOrderAggregate directly. This port
 *   is the configurable routing seam; the local impl is the safety fallback for B12+.
 *
 * Remote path (flag=true, B12+):
 *   AccountRemoteActivityAccountPort routes to account-service via Dubbo.
 *   Not enabled until end-to-end idempotency validation passes.
 */
public interface IActivityAccountPort {

    /**
     * Synchronously decrement total/month/day quota for a confirmed raffle participation.
     *
     * @param userId        user identifier
     * @param activityId    activity identifier
     * @param outBusinessNo idempotency key — must match the raffle order's outBusinessNo
     * @return true if quota was decremented (or already decremented for this key);
     *         false if quota is exhausted and the draw must be rejected
     */
    boolean decrementQuota(String userId, Long activityId, String outBusinessNo);

    /**
     * Rollback a previously decremented quota slot (saga compensation path).
     *
     * Safe to call even if the matching decrementQuota was never applied.
     *
     * @param userId        user identifier
     * @param activityId    activity identifier
     * @param outBusinessNo idempotency key — same value used in decrementQuota
     */
    void rollbackQuota(String userId, Long activityId, String outBusinessNo);

    /**
     * Read the available credit balance for a user (credit-purchase partake validation).
     *
     * prep: ActivityRepository must not directly import IUserCreditAccountDao.
     * Local implementation delegates to IUserCreditAccountDao; remote implementation
     * is 
     *
     * @param userId user identifier (used as shard key)
     * @return available credit amount; BigDecimal.ZERO if no account exists
     */
    BigDecimal queryUserCreditAccountAmount(String userId);

}

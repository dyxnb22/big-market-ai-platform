package com.dyx.market.domain.strategy.adapter.port;

/**
 * Domain port isolating StrategyRepository from direct activity-account DAO access.
 *
 * prep (AL-2/AL-3): StrategyRepository must not import
 * IRaffleActivityAccountDao or IRaffleActivityAccountDayDao.
 *
 * Both methods require shard routing by userId — callers pass the resolved
 * activityId (obtained via IRaffleActivityDao, AL-1 still allowed) so that
 * StrategyRepository stays stateless with respect to account storage.
 *
 * Local path (default): LocalStrategyActivityAccountPort delegates directly to
 * IRaffleActivityAccountDao / IRaffleActivityAccountDayDao with IDBRouterStrategy.
 *
 * Remote path (configurable): AccountRemoteStrategyActivityAccountPort
 * will call account-service read API once read endpoints are wired.
 */
public interface IStrategyActivityAccountPort {

    /**
     * Count how many times a user has raffled today for the given activity.
     *
     * @param userId     user identifier (shard key)
     * @param activityId activity identifier (resolved by caller from strategyId)
     * @return today's use count; 0 if no day account row exists
     */
    Integer queryTodayRaffleCount(String userId, Long activityId);

    /**
     * Count how many total raffle quota units the user has consumed for the
     * given activity (allotted minus remaining).
     *
     * @param userId     user identifier (shard key)
     * @param activityId activity identifier (resolved by caller from strategyId)
     * @return total use count
     */
    Integer queryTotalUseCount(String userId, Long activityId);

}

package com.dyx.market.domain.strategy.adapter.port;

/**
 * Domain port isolating StrategyRepository from direct activity DAO access
 * for the activityId <-> strategyId mapping.
 *
 * Phase 7-A (AL-1): StrategyRepository must not import IRaffleActivityDao.
 * The raffle_activity table is owned by activity-service; this port expresses
 * the two read projections strategy-service legitimately needs.
 *
 * Local path (default): LocalStrategyActivityMappingPort delegates directly
 * to IRaffleActivityDao. No shard routing is required — raffle_activity is not
 * sharded; both queries are simple primary-key lookups.
 *
 * Remote path (future, flag-gated): ActivityRemoteStrategyActivityMappingPort
 * will call activity-service read API once Phase 8-E read endpoints are wired.
 */
public interface IStrategyActivityMappingPort {

    /**
     * Look up the strategyId for a given activityId.
     *
     * @param activityId activity identifier
     * @return strategyId associated with the activity, or null if not found
     */
    Long queryStrategyIdByActivityId(Long activityId);

    /**
     * Look up the activityId for a given strategyId.
     *
     * @param strategyId strategy identifier
     * @return activityId associated with the strategy, or null if not found
     */
    Long queryActivityIdByStrategyId(Long strategyId);

}

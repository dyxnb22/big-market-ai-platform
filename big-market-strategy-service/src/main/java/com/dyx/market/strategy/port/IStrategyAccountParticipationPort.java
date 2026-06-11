package com.dyx.market.strategy.port;

/**
 * Narrow port for reading account participation counts inside strategy-service.
 *
 * Avoids direct activity/account domain imports inside the strategy bounded context.
 * Phase 4-D: supplies the real unlock/use-count values to StrategyReadServiceRPC
 * so remote responses match local RaffleStrategyController semantics.
 */
public interface IStrategyAccountParticipationPort {

    /**
     * Total number of times the user has consumed quota (used for rule-weight unlock progress).
     *
     * @param activityId activity identifier
     * @param userId     user identifier
     * @return total partake count, or 0 on failure
     */
    Integer queryRaffleActivityAccountPartakeCount(Long activityId, String userId);

    /**
     * Number of times the user has participated today (used for award unlock status).
     *
     * @param activityId activity identifier
     * @param userId     user identifier
     * @return daily partake count, or 0 on failure
     */
    Integer queryRaffleActivityAccountDayPartakeCount(Long activityId, String userId);

}

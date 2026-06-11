package com.dyx.market.trigger.api;

import com.dyx.market.trigger.api.dto.RaffleAwardListRequestDTO;
import com.dyx.market.trigger.api.dto.RaffleAwardListResponseDTO;
import com.dyx.market.trigger.api.dto.RaffleStrategyRuleWeightRequestDTO;
import com.dyx.market.trigger.api.dto.RaffleStrategyRuleWeightResponseDTO;
import com.dyx.market.trigger.api.response.Response;

import java.util.List;

/**
 * Read-only Dubbo contract for the strategy bounded context.
 *
 * Phase 4-B: narrow read-only surface exported by big-market-strategy-service.
 * Draw execution and stock mutation are intentionally excluded — those paths
 * remain in market-service until Phase 5.
 */
public interface IStrategyReadService {

    /**
     * Query award list for a given activity (by activityId → strategyId mapping).
     * Equivalent to the HTTP GET /raffle/strategy/query_raffle_award_list surface.
     *
     * @param request userId + activityId
     * @return ordered list of strategy awards with lock-count metadata
     */
    Response<List<RaffleAwardListResponseDTO>> queryRaffleAwardList(RaffleAwardListRequestDTO request);

    /**
     * Query rule-weight unlock progress for a given activity.
     * Equivalent to the HTTP GET /raffle/strategy/query_raffle_strategy_rule_weight surface.
     *
     * @param request userId + activityId
     * @return weight rule list with user's current unlock counts
     */
    Response<List<RaffleStrategyRuleWeightResponseDTO>> queryRaffleStrategyRuleWeight(RaffleStrategyRuleWeightRequestDTO request);

}

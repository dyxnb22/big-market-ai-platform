package com.dyx.market.trigger.adapter;

import com.dyx.market.trigger.api.dto.RaffleAwardListRequestDTO;
import com.dyx.market.trigger.api.dto.RaffleAwardListResponseDTO;
import com.dyx.market.trigger.api.dto.RaffleStrategyRuleWeightRequestDTO;
import com.dyx.market.trigger.api.dto.RaffleStrategyRuleWeightResponseDTO;

import java.util.List;

/**
 * Strategy read adapter boundary.
 *
 * Matches the two read methods in IStrategyReadService. Local implementation
 * delegates to IRaffleAward, IRaffleRule, and IAccountReadAdapter. Remote
 * implementation (StrategyRemoteReadAdapter in market-service) proxies to
 * big-market-strategy-service via Dubbo when strategy.service.remote-read.enabled=true.
 *
 * Phase 4-D: introduced so RaffleStrategyController no longer calls strategy
 * domain services directly for the two pure read endpoints.
 */
public interface IStrategyReadAdapter {

    /**
     * Query award list with unlock status for a given activity and user.
     *
     * @param request userId + activityId
     * @return ordered award list with lock-count metadata
     */
    List<RaffleAwardListResponseDTO> queryRaffleAwardList(RaffleAwardListRequestDTO request);

    /**
     * Query rule-weight unlock progress for a given activity and user.
     *
     * @param request userId + activityId
     * @return weight rule list with user's current unlock counts
     */
    List<RaffleStrategyRuleWeightResponseDTO> queryRaffleStrategyRuleWeight(RaffleStrategyRuleWeightRequestDTO request);

}

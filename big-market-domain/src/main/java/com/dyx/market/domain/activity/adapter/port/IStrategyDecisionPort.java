package com.dyx.market.domain.activity.adapter.port;

import com.dyx.market.domain.strategy.model.entity.RaffleAwardEntity;
import com.dyx.market.domain.strategy.model.entity.RaffleFactorEntity;

/**
 * Domain port for the in-draw strategy decision step.
 *
 * Domain contract for executing the strategy decision step.
 *
 * Design rationale:
 *   RaffleApplicationService previously injected IRaffleStrategy directly.
 *   This port is the configurable routing seam that allows the strategy decision
 *   step to be swapped between the local in-process call and a configured remote
 *   Dubbo call without changing the orchestrator.
 *
 * Local path (default):
 *   LocalStrategyDecisionPort (in big-market-infrastructure) delegates to the
 *   existing in-process IRaffleStrategy bean. Behavior is identical to the
 *   direct call. Active when no other IStrategyDecisionPort bean exists
 *   (@ConditionalOnMissingBean).
 *
 * Remote path (documented extension point):
 *   A configured StrategyRemoteDecisionPort can use the same contract when
 *   a remote strategy decision service is added to a learning run.
 */
public interface IStrategyDecisionPort {

    /**
     * Execute the strategy decision for a confirmed raffle participation.
     *
     * Semantics are identical to IRaffleStrategy.performRaffle: runs the
     * rule-chain and rule-tree, decrements Redis award stock, and returns
     * the selected award. The Redis stock decrement is non-reversible in
     * this batch; do not add retry or compensation logic here.
     *
     * @param factor raffle factor entity carrying userId, strategyId, endDateTime
     * @return the selected award entity
     */
    RaffleAwardEntity performRaffle(RaffleFactorEntity factor);

}

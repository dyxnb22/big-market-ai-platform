package com.dyx.market.infrastructure.adapter.port;

import com.dyx.market.domain.activity.adapter.port.IStrategyDecisionPort;
import com.dyx.market.domain.strategy.model.entity.RaffleAwardEntity;
import com.dyx.market.domain.strategy.model.entity.RaffleFactorEntity;
import com.dyx.market.domain.strategy.service.IRaffleStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * Local (in-process) implementation of IStrategyDecisionPort.
 *
 * delegates directly to the existing IRaffleStrategy bean,
 * preserving identical behavior to the pre-5-D direct injection in
 * RaffleApplicationService. No network hop. No behavior change.
 *
 * Active by default via @ConditionalOnMissingBean — if no other
 * IStrategyDecisionPort bean is registered this one is used. A configured remote
 * implementation in big-market-market-service (guarded by
 * strategy.service.remote-decision.enabled=false) would take precedence when
 * that flag is true. That remote implementation is documented extension point.
 */
@Slf4j
@Component
@ConditionalOnMissingBean(IStrategyDecisionPort.class)
public class LocalStrategyDecisionPort implements IStrategyDecisionPort {

    @Resource
    private IRaffleStrategy raffleStrategy;

    @Override
    public RaffleAwardEntity performRaffle(RaffleFactorEntity factor) {
        log.debug("[LocalStrategyDecisionPort] performRaffle userId:{} strategyId:{}",
                factor.getUserId(), factor.getStrategyId());
        return raffleStrategy.performRaffle(factor);
    }

}

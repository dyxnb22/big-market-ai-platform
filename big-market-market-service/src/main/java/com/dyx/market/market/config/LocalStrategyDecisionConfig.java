package com.dyx.market.market.config;

import com.dyx.market.domain.activity.adapter.port.IAwardFulfillmentPort;
import com.dyx.market.domain.activity.adapter.port.IStrategyDecisionPort;
import com.dyx.market.domain.award.service.IAwardService;
import com.dyx.market.domain.strategy.service.IRaffleStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LocalStrategyDecisionConfig {

    @Bean
    public IStrategyDecisionPort strategyDecisionPort(IRaffleStrategy raffleStrategy) {
        return raffleStrategy::performRaffle;
    }

    @Bean
    public IAwardFulfillmentPort awardFulfillmentPort(IAwardService awardService) {
        return awardService::saveUserAwardRecord;
    }

}

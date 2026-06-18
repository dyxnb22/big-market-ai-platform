package com.dyx.market.market.config;

import com.dyx.market.domain.activity.adapter.port.IAwardFulfillmentPort;
import com.dyx.market.domain.activity.adapter.port.IStrategyDecisionPort;
import com.dyx.market.domain.award.service.IAwardService;
import com.dyx.market.domain.strategy.service.IRaffleStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 本地策略决策与发奖履约端口配置。
 * <p>
 * 将领域服务方法引用注册为六边形端口 Bean，供活动抽奖流程注入使用。
 */
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

package com.dyx.market.account.config;

import com.dyx.market.domain.activity.adapter.port.IAwardFulfillmentPort;
import com.dyx.market.domain.activity.adapter.port.IStrategyDecisionPort;
import com.dyx.market.domain.award.service.IAwardService;
import com.dyx.market.domain.strategy.service.IRaffleStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 活动领域端口本地适配：将抽奖策略与发奖服务绑定为进程内实现。
 * <p>
 * 供账户服务独立运行时满足活动领域对 {@link IStrategyDecisionPort}、
 * {@link IAwardFulfillmentPort} 的依赖，无需跨服务 RPC。
 */
@Configuration
public class LocalActivityPortConfig {

    @Bean
    public IStrategyDecisionPort strategyDecisionPort(IRaffleStrategy raffleStrategy) {
        return raffleStrategy::performRaffle;
    }

    @Bean
    public IAwardFulfillmentPort awardFulfillmentPort(IAwardService awardService) {
        return awardService::saveUserAwardRecord;
    }

}

package com.dyx.market.trigger.application;

import com.dyx.market.domain.strategy.service.armory.IStrategyArmory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Slf4j
@Service
public class RaffleStrategyArmoryApplicationService {

    @Resource
    private IStrategyArmory strategyArmory;

    public boolean strategyArmory(Long strategyId) {
        log.info("抽奖策略装配开始 strategyId：{}", strategyId);
        boolean armoryStatus = strategyArmory.assembleLotteryStrategy(strategyId);
        log.info("抽奖策略装配完成 strategyId：{} status:{}", strategyId, armoryStatus);
        return armoryStatus;
    }
}

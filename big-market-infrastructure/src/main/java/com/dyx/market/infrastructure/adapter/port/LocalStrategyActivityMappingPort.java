package com.dyx.market.infrastructure.adapter.port;

import com.dyx.market.domain.strategy.adapter.port.IStrategyActivityMappingPort;
import com.dyx.market.infrastructure.dao.IRaffleActivityDao;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * Local (in-process) implementation of IStrategyActivityMappingPort.
 *
 * Phase 7-A (AL-1): StrategyRepository previously injected IRaffleActivityDao
 * directly. This port encapsulates those ID-mapping reads behind the strategy
 * domain boundary without altering behavior.
 *
 * No shard routing is required: raffle_activity is not sharded; both queries
 * are simple primary-key lookups identical to the original StrategyRepository
 * delegate calls.
 */
@Slf4j
@Component
public class LocalStrategyActivityMappingPort implements IStrategyActivityMappingPort {

    @Resource
    private IRaffleActivityDao raffleActivityDao;

    @Override
    public Long queryStrategyIdByActivityId(Long activityId) {
        return raffleActivityDao.queryStrategyIdByActivityId(activityId);
    }

    @Override
    public Long queryActivityIdByStrategyId(Long strategyId) {
        return raffleActivityDao.queryActivityIdByStrategyId(strategyId);
    }

}

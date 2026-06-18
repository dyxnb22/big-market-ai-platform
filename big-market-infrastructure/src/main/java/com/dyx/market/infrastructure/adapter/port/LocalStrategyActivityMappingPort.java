package com.dyx.market.infrastructure.adapter.port;

import com.dyx.market.domain.strategy.adapter.port.IStrategyActivityMappingPort;
import com.dyx.market.infrastructure.dao.IRaffleActivityDao;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * {@link IStrategyActivityMappingPort} 的本地（进程内）实现。
 *
 * <p>预备工作（AL-1）：原先 {@code StrategyRepository} 直接注入
 * {@code IRaffleActivityDao}；本端口将这些 ID 映射读操作封装在策略域边界之后，不改变行为。</p>
 *
 * <p>无需分片路由：{@code raffle_activity} 未分片，两次查询均为与原
 * {@code StrategyRepository} 委托调用相同的主键查找。</p>
 *
 * <p>激活条件：无远程替代实现时始终使用本本地端口（当前无对应远程 Bean）。</p>
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

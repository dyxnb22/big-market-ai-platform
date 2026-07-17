package com.dyx.market.domain.strategy.adapter.port;

/**
 * 领域端口：隔离 StrategyRepository 对活动 DAO 的直接依赖，提供 activityId 与 strategyId 的双向映射查询。
 * <p>
 * （AL-1）StrategyRepository 不得直接依赖 IRaffleActivityDao；raffle_activity 表归 activity-service 所有，
 * 本端口表达策略领域合法需要的两种读投影。
 * <p>
 * 本地路径（默认）：LocalStrategyActivityMappingPort 直接委托 IRaffleActivityDao，无需分片路由。
 * 当前实现由 LocalStrategyActivityMappingPort 提供本地读投影。
 */
public interface IStrategyActivityMappingPort {

    Long queryStrategyIdByActivityId(Long activityId);

    Long queryActivityIdByStrategyId(Long strategyId);

}

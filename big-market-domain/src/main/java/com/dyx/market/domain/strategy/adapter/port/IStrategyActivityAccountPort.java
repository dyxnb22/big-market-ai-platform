package com.dyx.market.domain.strategy.adapter.port;

/**
 * 领域端口：隔离 StrategyRepository 对活动账户 DAO 的直接依赖。
 * <p>
 * （AL-2/AL-3）StrategyRepository 不得直接依赖 IRaffleActivityAccountDao 或 IRaffleActivityAccountDayDao。
 * 两个方法均需按 userId 分片路由；调用方传入已解析的 activityId，使 StrategyRepository 与账户存储解耦。
 * <p>
 * 本地路径（默认）：LocalStrategyActivityAccountPort 经 IDBRouterStrategy 委托上述 DAO。
 * 当前实现由 LocalStrategyActivityAccountPort 提供本地读投影。
 */
public interface IStrategyActivityAccountPort {

    Integer queryTodayRaffleCount(String userId, Long activityId);

    Integer queryTotalUseCount(String userId, Long activityId);

}

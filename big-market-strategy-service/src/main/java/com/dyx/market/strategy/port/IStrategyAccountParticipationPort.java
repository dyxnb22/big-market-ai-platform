package com.dyx.market.strategy.port;

/**
 * 策略服务内读取账户参与次数的窄端口。
 * <p>
 * 避免策略限界上下文直接依赖 activity/account 领域；为 {@code StrategyReadServiceRPC}
 * 提供真实的解锁/使用次数，使远程响应与本地 RaffleStrategyController 语义一致。
 */
public interface IStrategyAccountParticipationPort {

    /**
     * 用户累计消耗额度次数（用于规则权重解锁进度）。
     *
     * @param activityId 活动 ID
     * @param userId     用户 ID
     * @return 累计参与次数，失败时返回 0
     */
    Integer queryRaffleActivityAccountPartakeCount(Long activityId, String userId);

    /**
     * 用户当日参与次数（用于奖品解锁状态）。
     *
     * @param activityId 活动 ID
     * @param userId     用户 ID
     * @return 当日参与次数，失败时返回 0
     */
    Integer queryRaffleActivityAccountDayPartakeCount(Long activityId, String userId);

}

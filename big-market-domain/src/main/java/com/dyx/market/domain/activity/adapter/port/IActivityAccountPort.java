package com.dyx.market.domain.activity.adapter.port;

import com.dyx.market.domain.activity.model.aggregate.CreatePartakeOrderAggregate;

import java.math.BigDecimal;

/**
 * 领域端口：活动账户配额的同步、幂等扣减。
 * <p>
 * decrementQuota 是抽奖前门禁，必须同步返回，以便 RaffleActivityPartakeService 在配额耗尽时立即拒绝抽奖；
 * 不同于 creditAward 的纯异步发件箱模式。
 * <p>
 * 幂等性：调用方以 outBusinessNo（抽奖订单 ID）为幂等键，重复调用安全，第二次返回 true 且不会重复扣减。
 * Saga 补偿：rollbackQuota 在下游抽奖失败时恢复已扣减的配额，即使从未执行过扣减也可安全调用。
 * <p>
 * 本地开发与 Docker 部署分别由 Spring Profile 选择实现：本地实现保持同库事务，Docker
 * 实现经 Dubbo 路由至 account-service。调用方不再通过运行时开关混合两条链路。
 */
public interface IActivityAccountPort {

    /**
     * 同步扣减总/月/日配额。
     *
     * @return 扣减成功或已对该幂等键扣减过返回 true；配额不足返回 false，抽奖应被拒绝
     */
    boolean decrementQuota(String userId, Long activityId, String outBusinessNo);

    /**
     * 回滚先前扣减的配额（Saga 补偿路径）。
     */
    void rollbackQuota(String userId, Long activityId, String outBusinessNo);

    /**
     * 创建抽奖参与单并完成额度预留。
     * <p>本地实现将额度扣减与订单写入放在同一事务；远程实现使用扣减后写单的 Saga。</p>
     */
    void savePartakeOrder(CreatePartakeOrderAggregate aggregate);

    /**
     * 抽奖后续步骤失败时补偿参与单额度。
     */
    void compensatePartakeOrder(String userId, Long activityId, String orderId, java.util.Date orderTime);

    /**
     * 查询用户可用积分余额（积分购买参与资格校验）。
     * <p>
     * ActivityRepository 不得直接依赖 IUserCreditAccountDao；本地实现委托该 DAO，远程实现对接 account-service。
     */
    BigDecimal queryUserCreditAccountAmount(String userId);

}

package com.dyx.market.trigger.api;

import com.dyx.market.trigger.api.dto.AccountQuotaCreateOrderRequestDTO;
import com.dyx.market.trigger.api.dto.AccountQuotaDecrementRequestDTO;
import com.dyx.market.trigger.api.dto.AccountQuotaRollbackRequestDTO;
import com.dyx.market.trigger.api.dto.AccountQuotaUpdateOrderRequestDTO;
import com.dyx.market.trigger.api.dto.UnpaidActivityOrderResponseDTO;
import com.dyx.market.trigger.api.dto.UserActivityAccountResponseDTO;
import com.dyx.market.trigger.api.response.Response;

/**
 * 跨服务 Dubbo 接口：活动账户额度（quota）操作。
 *
 * <p>接口定义在本模块；Provider 实现在 big-market-account-service。
 * market-service 现有调用方仍走进程内领域服务，尚未默认路由到远程。</p>
 */
public interface IAccountQuotaService {

    /**
     * 创建额度充值订单。
     *
     * <p>默认实现已存在 Provider，但 account.service.remote-quota-write.enabled
     * 默认为 false 时，调用方不会路由到远程。</p>
     */
    Response<UnpaidActivityOrderResponseDTO> createOrder(AccountQuotaCreateOrderRequestDTO request);

    /**
     * 将额度订单标记为已发货（到账）。
     *
     * <p>默认实现；默认配置下无写流量路由到远程。</p>
     */
    Response<Boolean> updateOrder(AccountQuotaUpdateOrderRequestDTO request);

    /**
     * 查询用户在指定活动下的总/日/月额度账户。
     *
     * @param activityId 活动 ID
     * @param userId     用户 ID
     * @return 含总额度、日额度、月额度及各自剩余量的账户信息
     */
    Response<UserActivityAccountResponseDTO> queryActivityAccountEntity(Long activityId, String userId);

    /**
     * 查询用户已参与次数（总额度已消耗量）。
     *
     * @param activityId 活动 ID
     * @param userId     用户 ID
     * @return 已消耗的总参与次数
     */
    Response<Integer> queryRaffleActivityAccountPartakeCount(Long activityId, String userId);

    /**
     * 查询用户今日已参与次数。
     *
     * @param activityId 活动 ID
     * @param userId     用户 ID
     * @return 今日已消耗的参与次数
     */
    Response<Integer> queryRaffleActivityAccountDayPartakeCount(Long activityId, String userId);

    /**
     * 抽奖确认后扣减额度计数器（总/月/日）。
     *
     * <p>幂等：相同 outBusinessNo 重复调用安全。
     * 启用服务化模式时需 account-service 幂等台账 DDL 及冒烟验证。</p>
     */
    Response<Boolean> decrementQuota(AccountQuotaDecrementRequestDTO request);

    /**
     * 回滚先前扣减的额度（Saga 补偿）。
     *
     * <p>即使对应 decrementQuota 从未执行也可安全调用。
     * 启用服务化模式时需 account-service 幂等台账 DDL 及冒烟验证。</p>
     */
    Response<Boolean> rollbackQuota(AccountQuotaRollbackRequestDTO request);

}

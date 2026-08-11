package com.dyx.market.trigger.adapter;

import com.dyx.market.domain.activity.model.entity.ActivityAccountEntity;
import com.dyx.market.trigger.api.dto.CreditOrderResponseDTO;

import java.math.BigDecimal;
import java.util.List;

/**
 * 只读账户查询适配器契约。
 * <p>
 * 实现类由 Spring Profile 选择：本地 Profile 委托进程内领域服务，Docker Profile
 * 经 Dubbo 访问 account-service。远程调用失败不会静默切换到本地数据。
 * 仅读操作经此契约路由；写路径（createOrder、配额扣减、返利、发奖）仍走本地领域服务。
 */
public interface IAccountReadAdapter {

    BigDecimal queryUserCreditAccount(String userId);

    ActivityAccountEntity queryActivityAccountEntity(Long activityId, String userId);

    Integer queryRaffleActivityAccountPartakeCount(Long activityId, String userId);

    Integer queryRaffleActivityAccountDayPartakeCount(Long activityId, String userId);

    /**
     * 查询用户积分流水（按交易时间倒序，积分账本展示）。
     */
    List<CreditOrderResponseDTO> queryUserCreditOrders(String userId, int limit);

}

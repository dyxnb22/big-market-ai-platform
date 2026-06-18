package com.dyx.market.trigger.adapter;

import com.dyx.market.domain.activity.model.entity.ActivityAccountEntity;

import java.math.BigDecimal;

/**
 * 只读账户查询适配器契约。
 * <p>
 * 实现类根据 {@code account.service.remote-read.enabled} 将查询路由到本地领域服务，
 * 或经 Dubbo 访问 account-service。
 * 仅读操作经此契约路由；写路径（createOrder、配额扣减、返利、发奖）仍走本地领域服务。
 */
public interface IAccountReadAdapter {

    BigDecimal queryUserCreditAccount(String userId);

    ActivityAccountEntity queryActivityAccountEntity(Long activityId, String userId);

    Integer queryRaffleActivityAccountPartakeCount(Long activityId, String userId);

    Integer queryRaffleActivityAccountDayPartakeCount(Long activityId, String userId);

}

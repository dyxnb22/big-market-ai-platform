package com.dyx.market.trigger.adapter;

import com.dyx.market.domain.activity.model.entity.ActivityAccountEntity;

import java.math.BigDecimal;

/**
 * Read-only account query adapter interface.
 *
 * Implementations route queries to either the local domain service or
 * account-service via Dubbo, controlled by account.service.remote-read.enabled.
 *
 * Phase 2.2-B1: only read operations are routed here. Write paths (createOrder,
 * quota decrement, rebate, award) remain on local domain services.
 */
public interface IAccountReadAdapter {

    BigDecimal queryUserCreditAccount(String userId);

    ActivityAccountEntity queryActivityAccountEntity(Long activityId, String userId);

    Integer queryRaffleActivityAccountPartakeCount(Long activityId, String userId);

    Integer queryRaffleActivityAccountDayPartakeCount(Long activityId, String userId);

}

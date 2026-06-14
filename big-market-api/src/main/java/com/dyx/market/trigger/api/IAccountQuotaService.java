package com.dyx.market.trigger.api;

import com.dyx.market.trigger.api.dto.AccountQuotaCreateOrderRequestDTO;
import com.dyx.market.trigger.api.dto.AccountQuotaDecrementRequestDTO;
import com.dyx.market.trigger.api.dto.AccountQuotaRollbackRequestDTO;
import com.dyx.market.trigger.api.dto.AccountQuotaUpdateOrderRequestDTO;
import com.dyx.market.trigger.api.dto.UnpaidActivityOrderResponseDTO;
import com.dyx.market.trigger.api.dto.UserActivityAccountResponseDTO;
import com.dyx.market.trigger.api.response.Response;

/**
 * Cross-service Dubbo API for activity account quota operations.
 *
 * interface declared here; provider lives in
 * big-market-account-service. Existing callers in market-service still call
 * domain services in-process — no final routing yet.
 */
public interface IAccountQuotaService {

    /**
     * Create a quota recharge order.
     *
     * default implementation: provider exists, but callers are not routed to it
     * while account.service.remote-quota-write.enabled defaults false.
     */
    Response<UnpaidActivityOrderResponseDTO> createOrder(AccountQuotaCreateOrderRequestDTO request);

    /**
     * Mark a quota order as delivered.
     *
     * default implementation; no write traffic is routed by default.
     */
    Response<Boolean> updateOrder(AccountQuotaUpdateOrderRequestDTO request);

    /**
     * Query total/day/month quota account for a user in an activity.
     *
     * @param activityId activity identifier
     * @param userId     user identifier
     * @return quota account entity with total, day, and month counts and surpluses
     */
    Response<UserActivityAccountResponseDTO> queryActivityAccountEntity(Long activityId, String userId);

    /**
     * Query how many times the user has already participated (total quota consumed).
     *
     * @param activityId activity identifier
     * @param userId     user identifier
     * @return total partake count consumed
     */
    Response<Integer> queryRaffleActivityAccountPartakeCount(Long activityId, String userId);

    /**
     * Query how many times the user has participated today.
     *
     * @param activityId activity identifier
     * @param userId     user identifier
     * @return daily partake count consumed today
     */
    Response<Integer> queryRaffleActivityAccountDayPartakeCount(Long activityId, String userId);

    /**
     * Decrement quota counters (total/month/day) after a confirmed raffle participation.
     *
     * Idempotent: repeated calls with the same outBusinessNo are safe.
     *
     * Requires the account-service idempotency ledger DDL and smoke validation
     * when this service-oriented mode is enabled.
     */
    Response<Boolean> decrementQuota(AccountQuotaDecrementRequestDTO request);

    /**
     * Rollback a previously decremented quota slot (saga compensation).
     *
     * Safe to call even if the matching decrementQuota was never applied.
     *
     * Requires the account-service idempotency ledger DDL and smoke validation
     * when this service-oriented mode is enabled.
     */
    Response<Boolean> rollbackQuota(AccountQuotaRollbackRequestDTO request);

}

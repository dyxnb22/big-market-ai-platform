package com.dyx.market.trigger.api;

import com.dyx.market.trigger.api.dto.FulfillmentDistributeAwardRequestDTO;
import com.dyx.market.trigger.api.dto.FulfillmentSaveUserAwardRecordRequestDTO;
import com.dyx.market.trigger.api.response.Response;

/**
 * Cross-service Dubbo API for award fulfillment operations.
 *
 * Provider lives in big-market-fulfillment-service. Each method delegates to the
 * local IAwardService domain bean and wraps the result in the standard
 * {@link Response} envelope with the same error-handling conventions as
 * {@link IAccountCreditService}, {@link IAccountQuotaService}, and others in
 * this package.
 *
 * Local (in-process) callers in market-service, message-job-service, and
 * infrastructure still invoke IAwardService directly — no traffic is routed
 * through this interface until the award-credit outbox is local smoke validated
 * and the remote-award flag is enabled.
 *
 * @see com.dyx.market.domain.award.service.IAwardService
 */
public interface IFulfillmentAwardService {

    /**
     * Persist a user's award record and emit the award-dispatch event.
     *
     * @param request userId, activityId, strategyId, orderId, awardId,
     *                awardTitle, awardTime, awardState, awardConfig
     * @return success / error via {@code Response<Void>}
     */
    Response<Void> saveUserAwardRecord(FulfillmentSaveUserAwardRecordRequestDTO request);

    /**
     * Dispatch (deliver) a prize to the user.
     *
     * @param request userId, orderId, awardId, awardConfig
     * @return success / error via {@code Response<Void>}
     */
    Response<Void> distributeAward(FulfillmentDistributeAwardRequestDTO request);

}

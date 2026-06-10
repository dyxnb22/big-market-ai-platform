package com.dyx.market.trigger.adapter;

import com.dyx.market.domain.award.model.entity.DistributeAwardEntity;

/**
 * Award dispatch routing adapter — Phase 2.3-B.
 *
 * Seam between message-job-service's SendAwardConsumer and the fulfillment-service
 * Dubbo provider. Default implementation delegates to the local IAwardService bean.
 * Remote Dubbo call is active only when account.fulfillment.remote-award.enabled=true.
 *
 * Enabling the remote path requires:
 *   1. Phase 2.2 staging GO (B17 evidence)
 *   2. Award credit outbox DDL applied and staging-validated (award-credit-outbox.enabled=true)
 */
public interface IAwardDispatchAdapter {

    void distributeAward(DistributeAwardEntity distributeAwardEntity) throws Exception;

}

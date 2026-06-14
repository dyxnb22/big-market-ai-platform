package com.dyx.market.domain.activity.adapter.port;

import com.dyx.market.domain.award.model.entity.UserAwardRecordEntity;

/**
 * Domain port for persisting raffle award fulfillment records.
 *
 * Domain contract for writing award fulfillment records.
 *
 * Local path (default):
 *   LocalAwardFulfillmentPort delegates to IAwardService.saveUserAwardRecord,
 *   preserving the current local transaction that writes user_award_record and
 *   the task outbox row together.
 *
 * Remote path (documented extension point):
 *   A remote implementation can use the same contract when the award write path
 *   is run through a dedicated fulfillment service.
 */
public interface IAwardFulfillmentPort {

    /**
     * Persist a user award record and its existing fulfillment outbox side effect.
     *
     * @param userAwardRecord user award record produced by the draw flow
     */
    void saveUserAwardRecord(UserAwardRecordEntity userAwardRecord);

}

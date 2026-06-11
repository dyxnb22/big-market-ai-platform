package com.dyx.market.domain.activity.adapter.port;

import com.dyx.market.domain.award.model.entity.UserAwardRecordEntity;

/**
 * Domain port for persisting raffle award fulfillment records.
 *
 * Phase 5-E contract.
 *
 * Local path (default):
 *   LocalAwardFulfillmentPort delegates to IAwardService.saveUserAwardRecord,
 *   preserving the current local transaction that writes user_award_record and
 *   the task outbox row together.
 *
 * Remote path (future, NOT introduced in this batch):
 *   No remote implementation or remote flag exists yet. Any remote write path
 *   must wait for the Phase 5-G saga/outbox design.
 */
public interface IAwardFulfillmentPort {

    /**
     * Persist a user award record and its existing fulfillment outbox side effect.
     *
     * @param userAwardRecord user award record produced by the draw flow
     */
    void saveUserAwardRecord(UserAwardRecordEntity userAwardRecord);

}

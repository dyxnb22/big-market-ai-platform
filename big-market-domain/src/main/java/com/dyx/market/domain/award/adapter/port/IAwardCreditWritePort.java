package com.dyx.market.domain.award.adapter.port;

import java.math.BigDecimal;

/**
 * Domain port isolating AwardRepository from direct credit-account and
 * credit-award outbox DAO writes.
 *
 * Phase 7-A prep (AL-6/AL-11): fulfillment keeps the existing local transaction,
 * lock, and shard routing, while the credit table writes are hidden behind this
 * narrow boundary. The local implementation delegates to IUserCreditAccountDao
 * and ICreditAwardTaskDao without enabling remote traffic.
 */
public interface IAwardCreditWritePort {

    /**
     * Create the user credit account or add the award amount to an existing
     * account. Same affected-row and DuplicateKeyException behavior as the
     * previous AwardRepository direct DAO calls.
     *
     * @param userId user identifier; routing is owned by caller
     * @param creditAmount credit amount to add
     */
    void updateOrCreateCreditAccount(String userId, BigDecimal creditAmount);

    /**
     * Insert the credit-award outbox task for the currently selected shard.
     *
     * @param userId user identifier; routing is owned by caller
     * @param awardOrderId award order id used as idempotency key
     * @param creditAmount credit amount to dispatch
     */
    void insertCreditAwardTask(String userId, String awardOrderId, BigDecimal creditAmount);

}

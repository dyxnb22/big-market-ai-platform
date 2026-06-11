package com.dyx.market.domain.credit.adapter.port;

import com.dyx.market.domain.credit.model.entity.CreditAwardTaskEntity;

import java.util.List;

/**
 * Domain port isolating DispatchCreditAwardTaskJob from direct credit-award
 * outbox DAO access.
 *
 * Phase 7-A prep (AL-7): message-job-service must not import
 * ICreditAwardTaskDao directly. The credit_award_task table is owned by the
 * account/credit boundary; the job only needs pending reads and state updates.
 *
 * Local path (default): LocalCreditAwardTaskDispatchPort delegates directly to
 * ICreditAwardTaskDao. The job keeps the existing DB/TB routing and flag gate.
 *
 * Remote path (future, flag-gated): account-service API can replace the local
 * implementation before account-service owns credit outbox dispatch at runtime.
 */
public interface ICreditAwardTaskDispatchPort {

    /**
     * Query pending credit-award outbox tasks for the currently selected DB/TB shard.
     *
     * @return pending tasks; same ordering, limit, and retry filter as the mapper
     */
    List<CreditAwardTaskEntity> queryPendingTasks();

    /**
     * Mark a task as dispatched.
     *
     * @param task task carrying userId and awardOrderId
     * @return affected row count; same semantics as ICreditAwardTaskDao
     */
    int updateDispatched(CreditAwardTaskEntity task);

    /**
     * Increment retry count and mark failed when the retry ceiling is reached.
     *
     * @param task task carrying userId and awardOrderId
     * @return affected row count; same semantics as ICreditAwardTaskDao
     */
    int updateRetryFailed(CreditAwardTaskEntity task);

}

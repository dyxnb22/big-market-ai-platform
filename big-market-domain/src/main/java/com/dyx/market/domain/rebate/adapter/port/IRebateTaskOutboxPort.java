package com.dyx.market.domain.rebate.adapter.port;

import com.dyx.market.domain.rebate.model.entity.TaskEntity;

/**
 * Rebate task outbox boundary.
 *
 * Default local implementation preserves the legacy shared task table until
 * rebate_task_outbox DDL and cutover flags are externally approved.
 */
public interface IRebateTaskOutboxPort {

    void insert(TaskEntity taskEntity);

    void markSendMessageCompleted(TaskEntity taskEntity);

    void markSendMessageFail(TaskEntity taskEntity);

}

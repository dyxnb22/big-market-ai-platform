package com.dyx.market.domain.award.adapter.port;

import com.dyx.market.domain.award.model.entity.TaskEntity;

/**
 * Award-dispatch task outbox boundary.
 *
 * Default local implementation preserves the legacy shared task table until
 * award_dispatch_task_outbox DDL and cutover flags are externally approved.
 */
public interface IAwardDispatchTaskOutboxPort {

    void insert(TaskEntity taskEntity);

    void markSendMessageCompleted(TaskEntity taskEntity);

    void markSendMessageFail(TaskEntity taskEntity);

}

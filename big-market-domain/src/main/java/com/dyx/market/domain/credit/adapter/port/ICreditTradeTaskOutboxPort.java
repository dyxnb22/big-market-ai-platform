package com.dyx.market.domain.credit.adapter.port;

import com.dyx.market.domain.credit.model.entity.TaskEntity;

/**
 * Credit-trade task outbox boundary.
 *
 * Default local implementation preserves the shared task table until
 * credit_trade_task_outbox DDL and routing flags are validated for local learning.
 */
public interface ICreditTradeTaskOutboxPort {

    void insert(TaskEntity taskEntity);

    void markSendMessageCompleted(TaskEntity taskEntity);

    void markSendMessageFail(TaskEntity taskEntity);

}

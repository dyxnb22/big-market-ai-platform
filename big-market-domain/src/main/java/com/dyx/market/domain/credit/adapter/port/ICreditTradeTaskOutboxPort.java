package com.dyx.market.domain.credit.adapter.port;

import com.dyx.market.domain.credit.model.entity.TaskEntity;

/**
 * 积分交易任务发件箱边界。
 * <p>
 * 默认本地实现沿用共享 task 表，待 credit_trade_task_outbox DDL 与路由开关
 * 在本地学习环境验证后再切换。
 */
public interface ICreditTradeTaskOutboxPort {

    void insert(TaskEntity taskEntity);

    void markSendMessageCompleted(TaskEntity taskEntity);

    void markSendMessageFail(TaskEntity taskEntity);

}

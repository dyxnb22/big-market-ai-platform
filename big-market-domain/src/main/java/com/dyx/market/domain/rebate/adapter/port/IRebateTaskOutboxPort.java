package com.dyx.market.domain.rebate.adapter.port;

import com.dyx.market.domain.rebate.model.entity.TaskEntity;

/**
 * 返利任务发件箱边界。
 * <p>
 * 默认本地实现沿用共享 task 表，待 rebate_task_outbox DDL 与路由开关
 * 在本地学习环境验证后再切换。
 */
public interface IRebateTaskOutboxPort {

    void insert(TaskEntity taskEntity);

    void markSendMessageCompleted(TaskEntity taskEntity);

    void markSendMessageFail(TaskEntity taskEntity);

}

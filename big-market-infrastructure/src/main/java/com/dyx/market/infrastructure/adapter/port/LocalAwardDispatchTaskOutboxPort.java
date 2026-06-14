package com.dyx.market.infrastructure.adapter.port;

import com.alibaba.fastjson.JSON;
import com.dyx.market.domain.award.adapter.port.IAwardDispatchTaskOutboxPort;
import com.dyx.market.domain.award.model.entity.TaskEntity;
import com.dyx.market.infrastructure.dao.ITaskDao;
import com.dyx.market.infrastructure.dao.po.Task;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * Legacy local award-dispatch outbox adapter.
 *
 * Delegates to ITaskDao intentionally so can remove direct repository
 * DAO coupling without switching physical tables before DBA-applied DDL.
 */
@Component
public class LocalAwardDispatchTaskOutboxPort implements IAwardDispatchTaskOutboxPort {

    @Resource
    private ITaskDao taskDao;

    @Override
    public void insert(TaskEntity taskEntity) {
        taskDao.insert(buildTask(taskEntity, true));
    }

    @Override
    public void markSendMessageCompleted(TaskEntity taskEntity) {
        taskDao.updateTaskSendMessageCompleted(buildTask(taskEntity, false));
    }

    @Override
    public void markSendMessageFail(TaskEntity taskEntity) {
        taskDao.updateTaskSendMessageFail(buildTask(taskEntity, false));
    }

    private Task buildTask(TaskEntity taskEntity, boolean includePayload) {
        Task task = new Task();
        task.setUserId(taskEntity.getUserId());
        task.setTopic(taskEntity.getTopic());
        task.setMessageId(taskEntity.getMessageId());
        if (includePayload) {
            task.setMessage(JSON.toJSONString(taskEntity.getMessage()));
            task.setState(taskEntity.getState().getCode());
        }
        return task;
    }

}

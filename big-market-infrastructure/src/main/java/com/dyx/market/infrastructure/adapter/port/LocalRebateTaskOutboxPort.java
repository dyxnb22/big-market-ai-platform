package com.dyx.market.infrastructure.adapter.port;

import com.alibaba.fastjson.JSON;
import com.dyx.market.domain.rebate.adapter.port.IRebateTaskOutboxPort;
import com.dyx.market.domain.rebate.model.entity.TaskEntity;
import com.dyx.market.infrastructure.dao.ITaskDao;
import com.dyx.market.infrastructure.dao.po.Task;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 返利任务 Outbox 的遗留本地适配器。
 *
 * <p>实现 {@link IRebateTaskOutboxPort}，有意委托给 {@code ITaskDao}，
 * 以便在 DBA 执行 DDL 切换物理表之前，解除仓储层对 DAO 的直接耦合。</p>
 *
 * <p>激活条件：无远程替代实现时始终使用本本地端口（当前无对应远程 Bean）。</p>
 *
 * <p>Outbox 说明：通过 {@code task} 表记录消息发送状态（插入、标记完成、标记失败），
 * 幂等性由调用方传入的 {@code messageId} 与 DAO 层更新语义共同保障。</p>
 */
@Component
public class LocalRebateTaskOutboxPort implements IRebateTaskOutboxPort {

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

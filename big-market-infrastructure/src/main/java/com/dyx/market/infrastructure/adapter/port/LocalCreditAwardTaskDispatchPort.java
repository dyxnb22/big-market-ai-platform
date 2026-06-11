package com.dyx.market.infrastructure.adapter.port;

import com.dyx.market.domain.credit.adapter.port.ICreditAwardTaskDispatchPort;
import com.dyx.market.domain.credit.model.entity.CreditAwardTaskEntity;
import com.dyx.market.infrastructure.dao.ICreditAwardTaskDao;
import com.dyx.market.infrastructure.dao.po.CreditAwardTask;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

/**
 * Local (in-process) implementation of ICreditAwardTaskDispatchPort.
 *
 * Phase 7-A prep (AL-7): DispatchCreditAwardTaskJob previously injected
 * ICreditAwardTaskDao directly. This port preserves the same DAO calls while
 * hiding the infra DAO from message-job-service.
 */
@Slf4j
@Component
public class LocalCreditAwardTaskDispatchPort implements ICreditAwardTaskDispatchPort {

    @Resource
    private ICreditAwardTaskDao creditAwardTaskDao;

    @Override
    public List<CreditAwardTaskEntity> queryPendingTasks() {
        List<CreditAwardTask> tasks = creditAwardTaskDao.queryPendingTasks();
        List<CreditAwardTaskEntity> entities = new ArrayList<>(tasks.size());
        for (CreditAwardTask task : tasks) {
            entities.add(toEntity(task));
        }
        return entities;
    }

    @Override
    public int updateDispatched(CreditAwardTaskEntity task) {
        return creditAwardTaskDao.updateDispatched(toPo(task));
    }

    @Override
    public int updateRetryFailed(CreditAwardTaskEntity task) {
        return creditAwardTaskDao.updateRetryFailed(toPo(task));
    }

    private CreditAwardTaskEntity toEntity(CreditAwardTask task) {
        CreditAwardTaskEntity entity = new CreditAwardTaskEntity();
        entity.setId(task.getId());
        entity.setUserId(task.getUserId());
        entity.setAwardOrderId(task.getAwardOrderId());
        entity.setCreditAmount(task.getCreditAmount());
        entity.setState(task.getState());
        entity.setRetryCount(task.getRetryCount());
        entity.setCreateTime(task.getCreateTime());
        entity.setUpdateTime(task.getUpdateTime());
        return entity;
    }

    private CreditAwardTask toPo(CreditAwardTaskEntity entity) {
        CreditAwardTask task = new CreditAwardTask();
        task.setId(entity.getId());
        task.setUserId(entity.getUserId());
        task.setAwardOrderId(entity.getAwardOrderId());
        task.setCreditAmount(entity.getCreditAmount());
        task.setState(entity.getState());
        task.setRetryCount(entity.getRetryCount());
        task.setCreateTime(entity.getCreateTime());
        task.setUpdateTime(entity.getUpdateTime());
        return task;
    }

}

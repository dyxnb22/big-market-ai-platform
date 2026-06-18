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
 * {@link ICreditAwardTaskDispatchPort} 的本地（进程内）实现。
 *
 * <p>预备工作（AL-7）：原先 {@code DispatchCreditAwardTaskJob} 直接注入
 * {@code ICreditAwardTaskDao}；本端口保持相同的 DAO 调用，同时将基础设施 DAO
 * 对 message-job-service 隐藏。</p>
 *
 * <p>激活条件：无远程替代实现时始终使用本本地端口（当前无对应远程 Bean）。</p>
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

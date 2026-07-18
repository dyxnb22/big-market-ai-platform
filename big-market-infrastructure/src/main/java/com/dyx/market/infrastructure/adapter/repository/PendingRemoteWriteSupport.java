package com.dyx.market.infrastructure.adapter.repository;

import com.alibaba.fastjson.JSON;
import com.dyx.market.domain.activity.adapter.port.IPendingRemoteWritePort;
import com.dyx.market.infrastructure.dao.IPendingRemoteWriteTaskDao;
import com.dyx.market.infrastructure.dao.po.PendingRemoteWriteTask;
import com.dyx.market.middleware.db.router.DBRouterTemplate;
import com.dyx.market.middleware.db.router.strategy.IDBRouterStrategy;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 远程写失败时落库 pending 任务，供 {@code RemoteWriteReconcileJob} 对账重试。
 */
@Slf4j
@Component
public class PendingRemoteWriteSupport implements IPendingRemoteWritePort {

    @Resource
    private IPendingRemoteWriteTaskDao pendingRemoteWriteTaskDao;

    @Resource
    private IDBRouterStrategy dbRouter;

    /**
     * @return true if task is persisted or already exists; false only when arguments invalid
     * @throws RuntimeException when DB insert fails (caller must not claim task was recorded)
     */
    @Override
    public boolean enqueue(String outBusinessNo, String operation, Object payload, String userId) {
        if (StringUtils.isBlank(userId)) {
            return enqueue(outBusinessNo, operation, payload);
        }
        return DBRouterTemplate.executeOnShard(dbRouter, userId,
                () -> enqueue(outBusinessNo, operation, payload));
    }

    /**
     * @return true if task is persisted or already exists; false only when arguments invalid
     * @throws RuntimeException when DB insert fails (caller must not claim task was recorded)
     */
    @Override
    public boolean enqueue(String outBusinessNo, String operation, Object payload) {
        if (StringUtils.isBlank(outBusinessNo) || StringUtils.isBlank(operation) || payload == null) {
            return false;
        }
        try {
            pendingRemoteWriteTaskDao.insert(PendingRemoteWriteTask.builder()
                    .outBusinessNo(outBusinessNo)
                    .operation(operation)
                    .payload(JSON.toJSONString(payload))
                    .state("pending")
                    .retryCount(0)
                    .build());
            log.warn("[PendingRemoteWrite] enqueued outBusinessNo:{} operation:{}", outBusinessNo, operation);
            return true;
        } catch (DuplicateKeyException e) {
            log.warn("[PendingRemoteWrite] duplicate outBusinessNo:{} operation:{}", outBusinessNo, operation);
            return true;
        }
    }
}

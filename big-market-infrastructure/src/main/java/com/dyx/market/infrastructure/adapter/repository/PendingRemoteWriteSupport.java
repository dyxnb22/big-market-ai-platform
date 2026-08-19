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

import jakarta.annotation.Resource;

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
     * 将跨服务补偿任务写入中央 pending_remote_write_task 表。
     *
     * @return true 表示任务已持久化或已存在；仅参数非法时返回 false
     * @throws RuntimeException 数据库写入失败时抛出；调用方不能把失败当作任务已记录
     */
    @Override
    public boolean enqueue(String outBusinessNo, String operation, Object payload, String userId) {
        // 补偿任务必须能够跨过用户所在 market 分片的故障。
        // userId 仍保留在任务载荷中，但交接记录统一持久化到中央存储。
        return DBRouterTemplate.executeOnDb(dbRouter, 0,
                () -> enqueue(outBusinessNo, operation, payload));
    }

    /**
     * 在当前数据库路由下写入远程写补偿任务。
     *
     * @return true 表示任务已持久化或已存在；仅参数非法时返回 false
     * @throws RuntimeException 数据库写入失败时抛出；调用方不能把失败当作任务已记录
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

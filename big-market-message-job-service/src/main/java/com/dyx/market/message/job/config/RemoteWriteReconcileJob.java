package com.dyx.market.message.job.config;

import com.alibaba.fastjson.JSON;
import com.dyx.market.infrastructure.dao.IPendingRemoteWriteTaskDao;
import com.dyx.market.infrastructure.dao.po.PendingRemoteWriteTask;
import com.dyx.market.middleware.db.router.strategy.IDBRouterStrategy;
import com.dyx.market.trigger.api.IAccountCreditService;
import com.dyx.market.trigger.api.IAccountQuotaService;
import com.dyx.market.trigger.api.dto.*;
import com.dyx.market.trigger.api.response.Response;
import com.dyx.market.types.common.RemoteWriteOperations;
import com.dyx.market.types.enums.ResponseCode;
import com.xxl.job.core.handler.annotation.XxlJob;
import io.micrometer.core.annotation.Timed;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 远程写对账 Job：pending → continuation_pending → done。
 * continuation 失败保留 continuation_pending，不提前 mark done。
 */
@Slf4j
@Component
@Profile("docker")
public class RemoteWriteReconcileJob {

    private static final String STATE_CONTINUATION_PENDING = "continuation_pending";

    @Value("${job.remote-write-reconcile.max-retries:5}")
    private int maxRetries;

    @Value("${job.remote-write-reconcile.scan-limit:50}")
    private int scanLimit;

    @Resource
    private IPendingRemoteWriteTaskDao pendingRemoteWriteTaskDao;
    @Resource
    private IDBRouterStrategy dbRouter;
    @Resource
    private RedissonClient redissonClient;
    @Resource
    private RemoteWriteContinuationDispatcher remoteWriteContinuationDispatcher;

    @DubboReference(version = "1.0", check = false)
    private IAccountCreditService accountCreditService;
    @DubboReference(version = "1.0", check = false)
    private IAccountQuotaService accountQuotaService;
    @Timed(value = "RemoteWriteReconcileJob", description = "Pending remote write reconcile")
    @XxlJob("RemoteWriteReconcileJob")
    public void exec() {
        RLock lock = redissonClient.getLock("big-market-RemoteWriteReconcileJob");
        try {
            if (!lock.tryLock(3, 0, TimeUnit.SECONDS)) {
                return;
            }
            // db00 is the independent compensation store; 01/02 are scanned
            // for backwards compatibility with tasks written by older builds.
            for (int dbIdx = 0; dbIdx <= 2; dbIdx++) {
                dbRouter.setDBKey(dbIdx);
                List<PendingRemoteWriteTask> tasks = pendingRemoteWriteTaskDao.queryPendingTasks(maxRetries, scanLimit);
                for (PendingRemoteWriteTask task : tasks) {
                    try {
                        reconcile(task);
                    } finally {
                        // continuation / adapters may clear router — restore scan shard
                        dbRouter.setDBKey(dbIdx);
                    }
                }
            }
        } catch (Exception e) {
            log.error("[RemoteWriteReconcileJob] scan failed", e);
        } finally {
            dbRouter.clear();
            if (lock.isLocked() && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private void reconcile(PendingRemoteWriteTask task) {
        try {
            if (STATE_CONTINUATION_PENDING.equals(task.getState())) {
                remoteWriteContinuationDispatcher.dispatch(task);
                pendingRemoteWriteTaskDao.updateDone(task);
                log.info("[RemoteWriteReconcileJob] continuation done outBusinessNo:{} operation:{}",
                        task.getOutBusinessNo(), task.getOperation());
                return;
            }
            if (!isRemoteDone(task)) {
                retryRemoteWrite(task);
            }
            pendingRemoteWriteTaskDao.updateContinuationPending(task);
            task.setState(STATE_CONTINUATION_PENDING);
            remoteWriteContinuationDispatcher.dispatch(task);
            pendingRemoteWriteTaskDao.updateDone(task);
            log.info("[RemoteWriteReconcileJob] remote+continuation done outBusinessNo:{} operation:{}",
                    task.getOutBusinessNo(), task.getOperation());
        } catch (Exception e) {
            log.error("[RemoteWriteReconcileJob] reconcile failed outBusinessNo:{} operation:{} state:{} retry:{}",
                    task.getOutBusinessNo(), task.getOperation(), task.getState(), task.getRetryCount(), e);
            pendingRemoteWriteTaskDao.updateRetryFailed(task.getId(), maxRetries);
        }
    }

    private boolean isRemoteDone(PendingRemoteWriteTask task) {
        switch (task.getOperation()) {
            case RemoteWriteOperations.CREDIT_CREATE: {
                CreditTradeRequestDTO dto = JSON.parseObject(task.getPayload(), CreditTradeRequestDTO.class);
                Response<Boolean> resp = accountCreditService.existsCreditOrder(dto.getUserId(), dto.getOutBusinessNo());
                return resp != null && ResponseCode.SUCCESS.getCode().equals(resp.getCode()) && Boolean.TRUE.equals(resp.getData());
            }
            case RemoteWriteOperations.QUOTA_CREATE: {
                AccountQuotaCreateOrderRequestDTO dto = JSON.parseObject(task.getPayload(), AccountQuotaCreateOrderRequestDTO.class);
                Response<Boolean> resp = accountQuotaService.existsActivityOrder(dto.getUserId(), dto.getOutBusinessNo());
                return resp != null && ResponseCode.SUCCESS.getCode().equals(resp.getCode()) && Boolean.TRUE.equals(resp.getData());
            }
            case RemoteWriteOperations.QUOTA_UPDATE: {
                AccountQuotaUpdateOrderRequestDTO dto = JSON.parseObject(task.getPayload(), AccountQuotaUpdateOrderRequestDTO.class);
                Response<Boolean> resp = accountQuotaService.isActivityOrderCompleted(dto.getUserId(), dto.getOutBusinessNo());
                return resp != null && ResponseCode.SUCCESS.getCode().equals(resp.getCode()) && Boolean.TRUE.equals(resp.getData());
            }
            case RemoteWriteOperations.QUOTA_ROLLBACK:
                // rollbackQuota is ledger-idempotent. A retry is therefore both the
                // reconciliation probe and the repair action for an UNKNOWN result.
                return false;
            default:
                log.warn("[RemoteWriteReconcileJob] unknown operation:{} outBusinessNo:{}", task.getOperation(), task.getOutBusinessNo());
                return false;
        }
    }

    private void retryRemoteWrite(PendingRemoteWriteTask task) {
        switch (task.getOperation()) {
            case RemoteWriteOperations.CREDIT_CREATE: {
                CreditTradeRequestDTO dto = JSON.parseObject(task.getPayload(), CreditTradeRequestDTO.class);
                Response<String> resp = accountCreditService.createOrder(dto);
                if (resp != null && ResponseCode.INDEX_DUP.getCode().equals(resp.getCode())) {
                    return;
                }
                if (resp == null || !ResponseCode.SUCCESS.getCode().equals(resp.getCode())) {
                    throw new IllegalStateException("credit retry failed");
                }
                return;
            }
            case RemoteWriteOperations.QUOTA_CREATE: {
                AccountQuotaCreateOrderRequestDTO dto = JSON.parseObject(task.getPayload(), AccountQuotaCreateOrderRequestDTO.class);
                Response<UnpaidActivityOrderResponseDTO> resp = accountQuotaService.createOrder(dto);
                if (resp == null || !ResponseCode.SUCCESS.getCode().equals(resp.getCode())) {
                    throw new IllegalStateException("quota create retry failed");
                }
                return;
            }
            case RemoteWriteOperations.QUOTA_UPDATE: {
                AccountQuotaUpdateOrderRequestDTO dto = JSON.parseObject(task.getPayload(), AccountQuotaUpdateOrderRequestDTO.class);
                Response<Boolean> resp = accountQuotaService.updateOrder(dto);
                if (resp == null || !ResponseCode.SUCCESS.getCode().equals(resp.getCode())) {
                    throw new IllegalStateException("quota update retry failed");
                }
                return;
            }
            case RemoteWriteOperations.QUOTA_ROLLBACK: {
                AccountQuotaRollbackRequestDTO dto = JSON.parseObject(task.getPayload(), AccountQuotaRollbackRequestDTO.class);
                Response<Boolean> resp = accountQuotaService.rollbackQuota(dto);
                if (resp == null || !ResponseCode.SUCCESS.getCode().equals(resp.getCode())
                        || !Boolean.TRUE.equals(resp.getData())) {
                    throw new IllegalStateException("quota rollback retry failed");
                }
                return;
            }
            default:
                throw new IllegalStateException("unknown operation: " + task.getOperation());
        }
    }
}

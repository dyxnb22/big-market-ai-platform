package com.dyx.market.message.job.config;

import com.alibaba.fastjson.JSON;
import com.dyx.market.domain.rebate.support.RebateAppTokenValidator;
import com.dyx.market.infrastructure.dao.IPendingRemoteWriteTaskDao;
import com.dyx.market.infrastructure.dao.po.PendingRemoteWriteTask;
import com.dyx.market.middleware.db.router.strategy.IDBRouterStrategy;
import com.dyx.market.trigger.api.IAccountCreditService;
import com.dyx.market.trigger.api.IAccountQuotaService;
import com.dyx.market.trigger.api.IRebateService;
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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 远程写对账 Job：扫描 pending_remote_write_task，先查远程终态，未成功则重试同一 RPC。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "job.remote-write-reconcile.enabled", havingValue = "true", matchIfMissing = true)
public class RemoteWriteReconcileJob {

    @Value("${job.remote-write-reconcile.max-retries:5}")
    private int maxRetries;

    @Value("${job.remote-write-reconcile.scan-limit:50}")
    private int scanLimit;

    @Value("${rebate.service.remote-create-order.app-id:chatgpt-data}")
    private String rebateAppId;

    @Resource
    private IPendingRemoteWriteTaskDao pendingRemoteWriteTaskDao;
    @Resource
    private RebateAppTokenValidator rebateAppTokenValidator;
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
    @DubboReference(version = "1.0", check = false)
    private IRebateService rebateService;

    @Timed(value = "RemoteWriteReconcileJob", description = "Pending remote write reconcile")
    @XxlJob("RemoteWriteReconcileJob")
    public void exec() {
        RLock lock = redissonClient.getLock("big-market-RemoteWriteReconcileJob");
        try {
            if (!lock.tryLock(3, 0, TimeUnit.SECONDS)) {
                return;
            }
            for (int dbIdx = 1; dbIdx <= 2; dbIdx++) {
                dbRouter.setDBKey(dbIdx);
                List<PendingRemoteWriteTask> tasks = pendingRemoteWriteTaskDao.queryPendingTasks(maxRetries, scanLimit);
                for (PendingRemoteWriteTask task : tasks) {
                    reconcile(task);
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
            if (isRemoteDone(task)) {
                pendingRemoteWriteTaskDao.updateDone(task);
                remoteWriteContinuationDispatcher.dispatch(task);
                log.info("[RemoteWriteReconcileJob] remote already done outBusinessNo:{} operation:{}",
                        task.getOutBusinessNo(), task.getOperation());
                return;
            }
            retryRemoteWrite(task);
            pendingRemoteWriteTaskDao.updateDone(task);
            remoteWriteContinuationDispatcher.dispatch(task);
            log.info("[RemoteWriteReconcileJob] retry success outBusinessNo:{} operation:{}",
                    task.getOutBusinessNo(), task.getOperation());
        } catch (Exception e) {
            log.error("[RemoteWriteReconcileJob] reconcile failed outBusinessNo:{} operation:{} retry:{}",
                    task.getOutBusinessNo(), task.getOperation(), task.getRetryCount(), e);
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
            case RemoteWriteOperations.REBATE_CREATE: {
                RebateRequestDTO dto = JSON.parseObject(task.getPayload(), RebateRequestDTO.class);
                Response<Boolean> resp = rebateService.isCalendarSignRebate(rebateAppTokenValidator.buildRequest(rebateAppId,
                        RebateOrderQueryRequestDTO.builder()
                                .userId(dto.getUserId())
                                .outBusinessNo(dto.getOutBusinessNo())
                                .build()));
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
                if (resp == null || !ResponseCode.SUCCESS.getCode().equals(resp.getCode())) {
                    throw new IllegalStateException("credit retry failed");
                }
                return;
            }
            case RemoteWriteOperations.REBATE_CREATE: {
                RebateRequestDTO dto = JSON.parseObject(task.getPayload(), RebateRequestDTO.class);
                Response<Boolean> resp = rebateService.rebate(rebateAppTokenValidator.buildRequest(rebateAppId, dto));
                if (resp == null || !ResponseCode.SUCCESS.getCode().equals(resp.getCode())) {
                    throw new IllegalStateException("rebate retry failed");
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
            default:
                throw new IllegalStateException("unknown operation: " + task.getOperation());
        }
    }
}

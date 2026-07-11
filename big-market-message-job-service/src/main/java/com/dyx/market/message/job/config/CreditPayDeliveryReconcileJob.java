package com.dyx.market.message.job.config;

import com.dyx.market.domain.activity.adapter.repository.IActivityRepository;
import com.dyx.market.domain.activity.model.entity.DeliveryOrderEntity;
import com.dyx.market.domain.credit.model.entity.TradeEntity;
import com.dyx.market.domain.credit.model.valobj.TradeNameVO;
import com.dyx.market.domain.credit.model.valobj.TradeTypeVO;
import com.dyx.market.infrastructure.dao.IRaffleActivityOrderDao;
import com.dyx.market.infrastructure.dao.po.RaffleActivityOrder;
import com.dyx.market.infrastructure.redis.IRedisService;
import com.dyx.market.middleware.db.router.DBRouterTemplate;
import com.dyx.market.middleware.db.router.strategy.IDBRouterStrategy;
import com.dyx.market.trigger.adapter.IAccountCreditWriteAdapter;
import com.dyx.market.trigger.adapter.IAccountQuotaWriteAdapter;
import com.dyx.market.types.common.Constants;
import com.dyx.market.types.enums.ResponseCode;
import com.dyx.market.types.exception.AppException;
import com.xxl.job.core.handler.annotation.XxlJob;
import io.micrometer.core.annotation.Timed;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 积分兑换发货补偿 Job：扫描已扣积分但订单仍为 wait_pay 的悬挂单，重试发货或退款回滚。
 */
@Slf4j
@Component
public class CreditPayDeliveryReconcileJob {

    private static final String RETRY_KEY_PREFIX = "credit_pay_delivery_retry:";
    private static final String SKU_RESTORED_KEY_PREFIX = "credit_pay_sku_restored:";

    @Value("${job.credit-pay-delivery.max-retries:5}")
    private int maxRetries;

    @Value("${job.credit-pay-delivery.scan-limit:20}")
    private int scanLimit;

    @Value("${job.credit-pay-delivery.min-stuck-minutes:1}")
    private int minStuckMinutes;

    @Value("${job.credit-pay-delivery.restore-sku-stock:true}")
    private boolean restoreSkuStock;

    @Resource
    private IRaffleActivityOrderDao raffleActivityOrderDao;
    @Resource
    private IAccountQuotaWriteAdapter accountQuotaWriteAdapter;
    @Resource
    private IAccountCreditWriteAdapter accountCreditWriteAdapter;
    @Resource
    private IActivityRepository activityRepository;
    @Resource
    private IRedisService redisService;
    @Resource
    private IDBRouterStrategy dbRouter;
    @Resource
    private RedissonClient redissonClient;

    @Timed(value = "CreditPayDeliveryReconcileJob_DB1", description = "Credit pay delivery reconcile DB1")
    @XxlJob("CreditPayDeliveryReconcileJob_DB1")
    public void execDb01() {
        scanDb(1, "big-market-CreditPayDeliveryReconcileJob_DB1");
    }

    @Timed(value = "CreditPayDeliveryReconcileJob_DB2", description = "Credit pay delivery reconcile DB2")
    @XxlJob("CreditPayDeliveryReconcileJob_DB2")
    public void execDb02() {
        scanDb(2, "big-market-CreditPayDeliveryReconcileJob_DB2");
    }

    private void scanDb(int dbIdx, String lockName) {
        RLock lock = redissonClient.getLock(lockName);
        try {
            boolean isLocked = lock.tryLock(3, 0, TimeUnit.SECONDS);
            if (!isLocked) {
                return;
            }

            Date since = new Date(System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(minStuckMinutes));
            String tradeName = TradeNameVO.CONVERT_SKU.getName();

            for (int tbIdx = 0; tbIdx < 4; tbIdx++) {
                final int tableIdx = tbIdx;
                DBRouterTemplate.executeOnDbTb(dbRouter, dbIdx, tableIdx, () -> {
                    List<RaffleActivityOrder> stuckOrders = raffleActivityOrderDao.queryStuckWaitPayOrders(
                            since, tradeName, scanLimit);
                    for (RaffleActivityOrder order : stuckOrders) {
                        reconcileOrder(order);
                    }
                    List<RaffleActivityOrder> compensatingOrders = raffleActivityOrderDao.queryStuckCompensatingOrders(
                            since, scanLimit);
                    for (RaffleActivityOrder order : compensatingOrders) {
                        finishCompensatingOrder(order);
                    }
                });
            }
        } catch (Exception e) {
            log.error("[CreditPayDeliveryReconcileJob] DB{} scan failed", dbIdx, e);
        } finally {
            if (lock.isLocked() && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private void reconcileOrder(RaffleActivityOrder order) {
        String retryKey = RETRY_KEY_PREFIX + order.getUserId() + Constants.UNDERLINE + order.getOutBusinessNo();
        try {
            accountQuotaWriteAdapter.updateOrder(DeliveryOrderEntity.builder()
                    .userId(order.getUserId())
                    .outBusinessNo(order.getOutBusinessNo())
                    .build());
            redisService.remove(retryKey);
            log.info("[CreditPayDeliveryReconcileJob] delivery succeeded userId:{} outBusinessNo:{}",
                    order.getUserId(), order.getOutBusinessNo());
        } catch (Exception e) {
            long retries = redisService.incr(retryKey);
            redissonClient.getBucket(retryKey).expire(7, TimeUnit.DAYS);
            log.warn("[CreditPayDeliveryReconcileJob] delivery retry {}/{} failed userId:{} outBusinessNo:{}",
                    retries, maxRetries, order.getUserId(), order.getOutBusinessNo(), e);

            if (retries >= maxRetries) {
                compensateFailedDelivery(order);
            }
        }
    }

    private void compensateFailedDelivery(RaffleActivityOrder order) {
        try {
            dbRouter.doRouter(order.getUserId());
            RaffleActivityOrder compensatingReq = new RaffleActivityOrder();
            compensatingReq.setUserId(order.getUserId());
            compensatingReq.setOutBusinessNo(order.getOutBusinessNo());
            int claimed = raffleActivityOrderDao.updateOrderCompensating(compensatingReq);
            if (claimed != 1) {
                log.info("[CreditPayDeliveryReconcileJob] skip compensate, order not wait_pay userId:{} outBusinessNo:{}",
                        order.getUserId(), order.getOutBusinessNo());
                return;
            }
        } catch (Exception e) {
            log.error("[CreditPayDeliveryReconcileJob] claim compensating failed userId:{} outBusinessNo:{}",
                    order.getUserId(), order.getOutBusinessNo(), e);
            return;
        } finally {
            dbRouter.clear();
        }

        completeCompensation(order);
    }

    private void finishCompensatingOrder(RaffleActivityOrder order) {
        completeCompensation(order);
    }

    private void completeCompensation(RaffleActivityOrder order) {
        if (!refundCreditOnce(order)) {
            return;
        }
        if (!restoreSkuStockOnce(order)) {
            return;
        }
        markOrderFailed(order);
    }

    private boolean refundCreditOnce(RaffleActivityOrder order) {
        String refundOutBusinessNo = "refund_" + order.getOutBusinessNo();
        try {
            accountCreditWriteAdapter.createOrder(TradeEntity.builder()
                    .userId(order.getUserId())
                    .tradeName(TradeNameVO.CONVERT_SKU)
                    .tradeType(TradeTypeVO.FORWARD)
                    .amount(order.getPayAmount().abs())
                    .outBusinessNo(refundOutBusinessNo)
                    .build());
            log.info("[CreditPayDeliveryReconcileJob] credit refunded userId:{} outBusinessNo:{} refundKey:{}",
                    order.getUserId(), order.getOutBusinessNo(), refundOutBusinessNo);
            return true;
        } catch (AppException e) {
            if (ResponseCode.INDEX_DUP.getCode().equals(e.getCode())) {
                log.warn("[CreditPayDeliveryReconcileJob] refund already exists userId:{} refundKey:{}",
                        order.getUserId(), refundOutBusinessNo);
                return true;
            }
            log.error("[CreditPayDeliveryReconcileJob] refund failed userId:{} outBusinessNo:{}",
                    order.getUserId(), order.getOutBusinessNo(), e);
            return false;
        } catch (Exception e) {
            log.error("[CreditPayDeliveryReconcileJob] refund failed userId:{} outBusinessNo:{}",
                    order.getUserId(), order.getOutBusinessNo(), e);
            return false;
        }
    }

    private boolean restoreSkuStockOnce(RaffleActivityOrder order) {
        if (!restoreSkuStock || order.getSku() == null) {
            return true;
        }
        String skuKey = SKU_RESTORED_KEY_PREFIX + order.getUserId() + Constants.UNDERLINE + order.getOutBusinessNo();
        if (!Boolean.TRUE.equals(redisService.setNx(skuKey))) {
            log.info("[CreditPayDeliveryReconcileJob] SKU already restored userId:{} outBusinessNo:{}",
                    order.getUserId(), order.getOutBusinessNo());
            return true;
        }
        try {
            activityRepository.restoreActivitySkuStock(order.getSku());
            log.info("[CreditPayDeliveryReconcileJob] SKU stock restored sku:{}", order.getSku());
            return true;
        } catch (Exception e) {
            redisService.remove(skuKey);
            log.error("[CreditPayDeliveryReconcileJob] SKU stock restore failed sku:{}", order.getSku(), e);
            return false;
        }
    }

    private void markOrderFailed(RaffleActivityOrder order) {
        try {
            dbRouter.doRouter(order.getUserId());
            RaffleActivityOrder failedReq = new RaffleActivityOrder();
            failedReq.setUserId(order.getUserId());
            failedReq.setOutBusinessNo(order.getOutBusinessNo());
            int updated = raffleActivityOrderDao.updateOrderFailed(failedReq);
            if (updated == 1) {
                log.info("[CreditPayDeliveryReconcileJob] order marked failed userId:{} outBusinessNo:{}",
                        order.getUserId(), order.getOutBusinessNo());
            }
            redisService.remove(RETRY_KEY_PREFIX + order.getUserId() + Constants.UNDERLINE + order.getOutBusinessNo());
        } catch (Exception e) {
            log.error("[CreditPayDeliveryReconcileJob] mark order failed error userId:{} outBusinessNo:{}",
                    order.getUserId(), order.getOutBusinessNo(), e);
        } finally {
            dbRouter.clear();
        }
    }

}

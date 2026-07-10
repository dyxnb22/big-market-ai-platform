package com.dyx.market.infrastructure.adapter.repository;

import com.dyx.market.domain.activity.adapter.event.ActivitySkuStockZeroMessageEvent;
import com.dyx.market.domain.activity.model.valobj.ActivitySkuStockKeyVO;
import com.dyx.market.infrastructure.dao.IActivitySkuStockDecrementLedgerDao;
import com.dyx.market.infrastructure.dao.IRaffleActivitySkuDao;
import com.dyx.market.infrastructure.dao.po.ActivitySkuStockDecrementLedger;
import com.dyx.market.infrastructure.event.EventPublisher;
import com.dyx.market.infrastructure.redis.IRedisService;
import com.dyx.market.types.common.Constants;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBlockingQueue;
import org.redisson.api.RDelayedQueue;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 活动 SKU 库存缓存与队列操作，从 {@link ActivityRepository} 拆分以降低单类复杂度。
 */
@Slf4j
@Component
public class ActivitySkuStockCacheSupport {

    @Resource
    private IRedisService redisService;
    @Resource
    private IRaffleActivitySkuDao raffleActivitySkuDao;
    @Resource
    private IActivitySkuStockDecrementLedgerDao activitySkuStockDecrementLedgerDao;
    @Resource
    private ActivitySkuStockZeroMessageEvent activitySkuStockZeroMessageEvent;
    @Resource
    private EventPublisher eventPublisher;
    @Resource
    private TransactionTemplate transactionTemplate;

    public void cacheActivitySkuStockCount(String cacheKey, Integer stockCount) {
        if (redisService.isExists(cacheKey)) return;
        redisService.setAtomicLong(cacheKey, stockCount);
    }

    public boolean subtractionActivitySkuStock(Long sku, Long activityId, String cacheKey, Date endDateTime) {
        long surplus = redisService.decr(cacheKey);
        if (surplus < 0) {
            redisService.setAtomicLong(cacheKey, 0);
            return false;
        }

        String lockKey = cacheKey + Constants.UNDERLINE + surplus;
        long expireMillis = endDateTime.getTime() - System.currentTimeMillis() + TimeUnit.DAYS.toMillis(1);
        Boolean lock = redisService.setNx(lockKey, expireMillis, TimeUnit.MILLISECONDS);
        if (!Boolean.TRUE.equals(lock)) {
            log.warn("活动sku库存加锁失败，回滚扣减 surplus:{} lockKey:{}", surplus, lockKey);
            redisService.incr(cacheKey);
            return false;
        }

        if (surplus == 0) {
            eventPublisher.publish(activitySkuStockZeroMessageEvent.topic(), activitySkuStockZeroMessageEvent.buildEventMessage(sku));
        }

        activitySkuStockConsumeSendQueue(ActivitySkuStockKeyVO.builder()
                .sku(sku)
                .activityId(activityId)
                .lockSurplus(surplus)
                .build());
        return true;
    }

    public void activitySkuStockConsumeSendQueue(ActivitySkuStockKeyVO activitySkuStockKeyVO) {
        String cacheKey = Constants.RedisKey.ACTIVITY_SKU_COUNT_QUERY_KEY + Constants.UNDERLINE + activitySkuStockKeyVO.getSku();
        RBlockingQueue<ActivitySkuStockKeyVO> blockingQueue = redisService.getBlockingQueue(cacheKey);
        RDelayedQueue<ActivitySkuStockKeyVO> delayedQueue = redisService.getDelayedQueue(blockingQueue);
        delayedQueue.offer(activitySkuStockKeyVO, 3, TimeUnit.SECONDS);
        redisService.addToSet(Constants.RedisKey.ACTIVITY_SKU_STOCK_PENDING_SET, String.valueOf(activitySkuStockKeyVO.getSku()));
    }

    public ActivitySkuStockKeyVO takeQueueValue() {
        // Nothing is ever enqueued to the bare key — all writes go to the per-SKU key
        // (ACTIVITY_SKU_COUNT_QUERY_KEY + "_" + sku). This no-arg form always returns null.
        // Migrate callers to takeQueueValue(Long sku).
        log.warn("takeQueueValue() called without sku — this queue key is never written to; always returns null. Use takeQueueValue(Long sku) instead.");
        String cacheKey = Constants.RedisKey.ACTIVITY_SKU_COUNT_QUERY_KEY;
        RBlockingQueue<ActivitySkuStockKeyVO> destinationQueue = redisService.getBlockingQueue(cacheKey);
        return destinationQueue.poll();
    }

    public ActivitySkuStockKeyVO peekQueueValue(Long sku) {
        String cacheKey = Constants.RedisKey.ACTIVITY_SKU_COUNT_QUERY_KEY + Constants.UNDERLINE + sku;
        RBlockingQueue<ActivitySkuStockKeyVO> destinationQueue = redisService.getBlockingQueue(cacheKey);
        return destinationQueue.peek();
    }

    public void ackQueueValue(Long sku) {
        String cacheKey = Constants.RedisKey.ACTIVITY_SKU_COUNT_QUERY_KEY + Constants.UNDERLINE + sku;
        RBlockingQueue<ActivitySkuStockKeyVO> destinationQueue = redisService.getBlockingQueue(cacheKey);
        destinationQueue.poll();
        if (destinationQueue.isEmpty()) {
            redisService.removeFromSet(Constants.RedisKey.ACTIVITY_SKU_STOCK_PENDING_SET, String.valueOf(sku));
        }
    }

    public void syncActivitySkuStockFromQueue(Long sku) {
        ActivitySkuStockKeyVO stockKey = peekQueueValue(sku);
        if (null == stockKey) {
            redisService.removeFromSet(Constants.RedisKey.ACTIVITY_SKU_STOCK_PENDING_SET, String.valueOf(sku));
            return;
        }
        try {
            updateActivitySkuStockOnce(stockKey);
            ackQueueValue(sku);
        } catch (Exception e) {
            log.error("活动SKU库存落库失败，保留队列重试 sku:{}", sku, e);
        }
    }

    /**
     * Durable exactly-once MySQL decrement keyed by (sku, lockSurplus).
     * Redis SETNX is optional acceleration only.
     */
    public void updateActivitySkuStockOnce(ActivitySkuStockKeyVO stockKey) {
        if (null == stockKey || null == stockKey.getSku()) {
            return;
        }
        Long sku = stockKey.getSku();
        Long lockSurplus = stockKey.getLockSurplus();
        if (null == lockSurplus) {
            updateActivitySkuStock(sku);
            return;
        }

        String dedupeKey = "sku_mysql_decrement:" + sku + ":" + lockSurplus;
        if (!Boolean.TRUE.equals(redisService.setNx(dedupeKey, 7, TimeUnit.DAYS))) {
            ActivitySkuStockDecrementLedger existing = activitySkuStockDecrementLedgerDao.queryBySkuAndLockSurplus(
                    ActivitySkuStockDecrementLedger.builder().sku(sku).lockSurplus(lockSurplus).build());
            if (null != existing) {
                log.info("SKU 库存 MySQL 扣减已落账，跳过重复 sku:{} lockSurplus:{}", sku, lockSurplus);
                return;
            }
            redisService.remove(dedupeKey);
        }

        try {
            Boolean applied = transactionTemplate.execute(status -> {
                try {
                    activitySkuStockDecrementLedgerDao.insert(ActivitySkuStockDecrementLedger.builder()
                            .sku(sku)
                            .activityId(stockKey.getActivityId())
                            .lockSurplus(lockSurplus)
                            .build());
                } catch (DuplicateKeyException e) {
                    log.info("SKU 库存 ledger 已存在，跳过重复 sku:{} lockSurplus:{}", sku, lockSurplus);
                    return true;
                }
                updateActivitySkuStock(sku);
                return true;
            });
            if (!Boolean.TRUE.equals(applied)) {
                throw new IllegalStateException("activity sku stock ledger transaction returned false");
            }
            redisService.setNx(dedupeKey, 7, TimeUnit.DAYS);
        } catch (RuntimeException e) {
            redisService.remove(dedupeKey);
            throw e;
        }
    }

    public ActivitySkuStockKeyVO takeQueueValue(Long sku) {
        String cacheKey = Constants.RedisKey.ACTIVITY_SKU_COUNT_QUERY_KEY + Constants.UNDERLINE + sku;
        RBlockingQueue<ActivitySkuStockKeyVO> destinationQueue = redisService.getBlockingQueue(cacheKey);
        return destinationQueue.poll();
    }

    public void clearQueueValue() {
        String cacheKey = Constants.RedisKey.ACTIVITY_SKU_COUNT_QUERY_KEY;
        RBlockingQueue<ActivitySkuStockKeyVO> destinationQueue = redisService.getBlockingQueue(cacheKey);
        destinationQueue.clear();
    }

    public void clearQueueValue(Long sku) {
        String cacheKey = Constants.RedisKey.ACTIVITY_SKU_COUNT_QUERY_KEY + Constants.UNDERLINE + sku;
        RBlockingQueue<ActivitySkuStockKeyVO> destinationQueue = redisService.getBlockingQueue(cacheKey);
        destinationQueue.clear();
        redisService.removeFromSet(Constants.RedisKey.ACTIVITY_SKU_STOCK_PENDING_SET, String.valueOf(sku));
    }

    public void updateActivitySkuStock(Long sku) {
        raffleActivitySkuDao.updateActivitySkuStock(sku);
    }

    public void clearActivitySkuStock(Long sku) {
        raffleActivitySkuDao.clearActivitySkuStock(sku);
    }

    public void restoreActivitySkuStock(Long sku) {
        String cacheKey = Constants.RedisKey.ACTIVITY_SKU_STOCK_COUNT_KEY + sku;
        redisService.incr(cacheKey);
        log.info("SKU库存补偿恢复 sku:{} cacheKey:{}", sku, cacheKey);
    }

    public List<Long> querySkuList() {
        return raffleActivitySkuDao.querySkuList();
    }

    /** All SKUs with pending flush work (DB list ∪ Redis pending registry). */
    public List<Long> queryPendingSkuList() {
        Set<Long> seen = new HashSet<>();
        List<Long> merged = new ArrayList<>();
        List<Long> fromDb = querySkuList();
        if (null != fromDb) {
            for (Long sku : fromDb) {
                if (seen.add(sku)) {
                    merged.add(sku);
                }
            }
        }
        Set<String> pending = redisService.getSetMembers(Constants.RedisKey.ACTIVITY_SKU_STOCK_PENDING_SET);
        if (null != pending) {
            for (String member : pending) {
                try {
                    Long sku = Long.valueOf(member);
                    if (seen.add(sku)) {
                        merged.add(sku);
                    }
                } catch (NumberFormatException ignored) {
                    log.warn("invalid pending sku stock key: {}", member);
                }
            }
        }
        return merged;
    }
}

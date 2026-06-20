package com.dyx.market.infrastructure.adapter.repository;

import com.dyx.market.domain.activity.adapter.event.ActivitySkuStockZeroMessageEvent;
import com.dyx.market.domain.activity.model.valobj.ActivitySkuStockKeyVO;
import com.dyx.market.infrastructure.dao.IRaffleActivitySkuDao;
import com.dyx.market.infrastructure.event.EventPublisher;
import com.dyx.market.infrastructure.redis.IRedisService;
import com.dyx.market.types.common.Constants;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBlockingQueue;
import org.redisson.api.RDelayedQueue;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Date;
import java.util.List;
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
    private ActivitySkuStockZeroMessageEvent activitySkuStockZeroMessageEvent;
    @Resource
    private EventPublisher eventPublisher;

    public void cacheActivitySkuStockCount(String cacheKey, Integer stockCount) {
        if (redisService.isExists(cacheKey)) return;
        redisService.setAtomicLong(cacheKey, stockCount);
    }

    public boolean subtractionActivitySkuStock(Long sku, String cacheKey, Date endDateTime) {
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
        return true;
    }

    public void activitySkuStockConsumeSendQueue(ActivitySkuStockKeyVO activitySkuStockKeyVO) {
        String cacheKey = Constants.RedisKey.ACTIVITY_SKU_COUNT_QUERY_KEY + Constants.UNDERLINE + activitySkuStockKeyVO.getSku();
        RBlockingQueue<ActivitySkuStockKeyVO> blockingQueue = redisService.getBlockingQueue(cacheKey);
        RDelayedQueue<ActivitySkuStockKeyVO> delayedQueue = redisService.getDelayedQueue(blockingQueue);
        delayedQueue.offer(activitySkuStockKeyVO, 3, TimeUnit.SECONDS);
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
}

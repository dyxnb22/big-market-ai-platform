package com.dyx.market.infrastructure.adapter.repository;

import com.dyx.market.domain.activity.adapter.event.ActivitySkuStockZeroMessageEvent;
import com.dyx.market.domain.activity.model.valobj.ActivitySkuStockKeyVO;
import com.dyx.market.infrastructure.dao.IActivitySkuStockDecrementLedgerDao;
import com.dyx.market.infrastructure.dao.IActivitySkuStockRestoreLedgerDao;
import com.dyx.market.infrastructure.dao.IRaffleActivitySkuDao;
import com.dyx.market.infrastructure.dao.po.ActivitySkuStockDecrementLedger;
import com.dyx.market.infrastructure.dao.po.ActivitySkuStockRestoreLedger;
import com.dyx.market.infrastructure.event.EventPublisher;
import com.dyx.market.infrastructure.redis.IRedisService;
import com.dyx.market.types.common.Constants;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBlockingQueue;
import org.redisson.api.RDelayedQueue;
import org.redisson.api.RLock;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.annotation.Resource;
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
    private IActivitySkuStockRestoreLedgerDao activitySkuStockRestoreLedgerDao;
    @Resource
    private ActivitySkuStockZeroMessageEvent activitySkuStockZeroMessageEvent;
    @Resource
    private EventPublisher eventPublisher;
    @Resource
    private TransactionTemplate transactionTemplate;
    @Resource
    private RedissonClient redissonClient;

    public void cacheActivitySkuStockCount(String cacheKey, Integer stockCount) {
        if (redisService.isExists(cacheKey)) return;
        redisService.setAtomicLong(cacheKey, stockCount);
    }

    public boolean subtractionActivitySkuStock(Long sku, Long activityId, String cacheKey, Date endDateTime) {
        return subtractionActivitySkuStock(sku, activityId, cacheKey, endDateTime, null);
    }

    public boolean subtractionActivitySkuStock(Long sku, Long activityId, String cacheKey,
                                               Date endDateTime, String reservationId) {
        RLock reservationLock = redisService.getLock("activity_sku_reservation:" + sku);
        reservationLock.lock();
        try {
            Long current = redisService.getAtomicLong(cacheKey);
            if (current == null || current <= 0) {
                return false;
            }
            long expectedSurplus = current - 1;
            ActivitySkuStockDecrementLedger reservation = ActivitySkuStockDecrementLedger.builder()
                    .sku(sku).activityId(activityId).lockSurplus(expectedSurplus)
                    .reservationId(reservationId).status("reserved").build();
            try {
                activitySkuStockDecrementLedgerDao.insert(reservation);
            } catch (DuplicateKeyException duplicate) {
                ActivitySkuStockDecrementLedger existing = activitySkuStockDecrementLedgerDao
                        .queryBySkuAndLockSurplus(ActivitySkuStockDecrementLedger.builder()
                                .sku(sku).lockSurplus(expectedSurplus).build());
                if (existing != null && "released".equals(existing.getStatus())) {
                    // Redis 恢复后的库存槽位可能再次使用相同的剩余值。
                    // released 行只是历史事实，不能作为未来预占的幂等凭证。
                    activitySkuStockDecrementLedgerDao.deleteBySkuAndLockSurplus(existing);
                    activitySkuStockDecrementLedgerDao.insert(reservation);
                } else if (existing == null || !"reserved".equals(existing.getStatus())) {
                    // 已落账的预占已经持久化，不能再次扣减库存。
                    return existing != null;
                }
                // 进程可能在账本持久化后宕机；下面的 Redis 脚本会原子地恢复同一笔预占。
            }
            long lockTtlMillis = endDateTime == null
                    ? TimeUnit.DAYS.toMillis(30)
                    : Math.max(TimeUnit.SECONDS.toMillis(1),
                    endDateTime.getTime() - System.currentTimeMillis() + TimeUnit.DAYS.toMillis(1));
            Object scriptResult = redissonClient.getScript(StringCodec.INSTANCE).eval(RScript.Mode.READ_WRITE,
                    "local saved = redis.call('get', KEYS[1]); "
                            + "if saved then return tonumber(saved); end; "
                            + "local current = tonumber(redis.call('get', KEYS[2]) or '0'); "
                            + "if current <= 0 then return -1; end; "
                            + "local surplus = redis.call('decr', KEYS[2]); "
                            + "if surplus ~= tonumber(ARGV[1]) then redis.call('incr', KEYS[2]); return -2; end; "
                            + "local lockKey = KEYS[2] .. '_' .. surplus; "
                            + "local locked = redis.call('set', lockKey, '1', 'NX', 'PX', ARGV[2]); "
                            + "if not locked then redis.call('incr', KEYS[2]); return -2; end; "
                            + "redis.call('set', KEYS[1], surplus, 'EX', ARGV[3]); return surplus;",
                    RScript.ReturnType.INTEGER,
                    java.util.Arrays.asList("activity_reservation:" + sku + ":" + expectedSurplus, cacheKey),
                    // 使用 StringCodec，确保 ARGV 是纯数字；JsonJacksonCodec 会对值再做包装。
                    String.valueOf(expectedSurplus),
                    String.valueOf(lockTtlMillis),
                    String.valueOf(TimeUnit.DAYS.toSeconds(30)));
            long surplus = ((Number) scriptResult).longValue();
            if (surplus < 0) {
                activitySkuStockDecrementLedgerDao.updateStatusReleased(reservation);
                return false;
            }

            activitySkuStockConsumeSendQueue(ActivitySkuStockKeyVO.builder()
                    .sku(sku).activityId(activityId).lockSurplus(surplus)
                    .reservationId(reservationId).build());
            if (surplus == 0) {
                try {
                    eventPublisher.publish(activitySkuStockZeroMessageEvent.topic(), activitySkuStockZeroMessageEvent.buildEventMessage(sku));
                } catch (Exception eventEx) {
                    // 持久化库存队列已经接管这次扣减；库存归零通知故障不能把
                    // 已成功的预占变成调用方可见的第二次重试。
                    log.error("活动 SKU 库存归零通知失败，保留库存落账队列 sku:{}", sku, eventEx);
                }
            }
            return true;
        } finally {
            reservationLock.unlock();
        }
    }

    public void activitySkuStockConsumeSendQueue(ActivitySkuStockKeyVO activitySkuStockKeyVO) {
        String cacheKey = Constants.RedisKey.ACTIVITY_SKU_COUNT_QUERY_KEY + Constants.UNDERLINE + activitySkuStockKeyVO.getSku();
        RBlockingQueue<ActivitySkuStockKeyVO> blockingQueue = redisService.getBlockingQueue(cacheKey);
        RDelayedQueue<ActivitySkuStockKeyVO> delayedQueue = redisService.getDelayedQueue(blockingQueue);
        delayedQueue.offer(activitySkuStockKeyVO, 3, TimeUnit.SECONDS);
        redisService.addToSet(Constants.RedisKey.ACTIVITY_SKU_STOCK_PENDING_SET, String.valueOf(activitySkuStockKeyVO.getSku()));
    }

    public ActivitySkuStockKeyVO takeQueueValue() {
        // 当前不会向裸 key 入队，所有写入都使用按 SKU 区分的 key
        //（ACTIVITY_SKU_COUNT_QUERY_KEY + "_" + sku）。无参方法始终返回 null，调用方应迁移到
        // takeQueueValue(Long sku)。
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
            // 账本或 Redis 已成功、但延迟队列入队前发生宕机时，仍必须能够从 MySQL 恢复。
            for (ActivitySkuStockDecrementLedger reserved :
                    activitySkuStockDecrementLedgerDao.queryReservedBySku(sku)) {
                resumeReservedStock(reserved);
            }
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

    private void resumeReservedStock(ActivitySkuStockDecrementLedger reserved) {
        if (reserved.getLockSurplus() == null) {
            return;
        }
        String cacheKey = Constants.RedisKey.ACTIVITY_SKU_STOCK_COUNT_KEY + reserved.getSku();
        String markerKey = "activity_reservation:" + reserved.getSku() + ":" + reserved.getLockSurplus();
        if (!redisService.isExists(markerKey)) {
            Object result = redissonClient.getScript(StringCodec.INSTANCE).eval(RScript.Mode.READ_WRITE,
                    "local saved = redis.call('get', KEYS[1]); "
                            + "if saved then return tonumber(saved); end; "
                            + "local current = tonumber(redis.call('get', KEYS[2]) or '0'); "
                            + "if current <= 0 then return -1; end; "
                            + "local surplus = redis.call('decr', KEYS[2]); "
                            + "if surplus ~= tonumber(ARGV[1]) then redis.call('incr', KEYS[2]); return -2; end; "
                            + "redis.call('set', KEYS[1], surplus, 'EX', 2592000); return surplus;",
                    RScript.ReturnType.INTEGER,
                    java.util.Arrays.asList(markerKey, cacheKey), String.valueOf(reserved.getLockSurplus()));
            if (((Number) result).longValue() < 0) {
                activitySkuStockDecrementLedgerDao.updateStatusReleased(reserved);
                return;
            }
        }
        updateActivitySkuStockOnce(ActivitySkuStockKeyVO.builder()
                .sku(reserved.getSku()).activityId(reserved.getActivityId())
                .lockSurplus(reserved.getLockSurplus()).build());
    }

    /**
     * 按（sku，lockSurplus）执行 MySQL 持久化扣减，账本保证同一事件只落账一次。
     * Redis SETNX 仅用于可选的快速路径，不能作为最终一致性依据。
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
            if (null != existing && !"reserved".equals(existing.getStatus())) {
                log.info("SKU 库存 MySQL 扣减已落账，跳过重复 sku:{} lockSurplus:{}", sku, lockSurplus);
                return;
            }
            redisService.remove(dedupeKey);
        }

        try {
            Boolean applied = transactionTemplate.execute(status -> {
                ActivitySkuStockDecrementLedger existing = activitySkuStockDecrementLedgerDao
                        .queryBySkuAndLockSurplus(ActivitySkuStockDecrementLedger.builder()
                                .sku(sku).lockSurplus(lockSurplus).build());
                if (existing != null) {
                    if ("reserved".equals(existing.getStatus())) {
                        // 并发恢复可能在本次读取后释放该行，只有 CAS 状态迁移成功时
                        // 才允许执行实际库存扣减。
                        if (activitySkuStockDecrementLedgerDao.updateStatusApplied(existing) == 1) {
                            updateActivitySkuStock(sku);
                        }
                    }
                    return true;
                }
                try {
                    activitySkuStockDecrementLedgerDao.insert(ActivitySkuStockDecrementLedger.builder()
                            .sku(sku)
                            .activityId(stockKey.getActivityId())
                            .lockSurplus(lockSurplus)
                            .reservationId(stockKey.getReservationId())
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

    /**
     * 最多恢复同一笔预占一次。数据库行是持久化闸门，Redis 标记保证 Job 重试或进程重启后不会重复恢复。
     */
    public void restoreActivitySkuStock(Long sku, String reservationId) {
        if (sku == null || reservationId == null || reservationId.trim().isEmpty()) {
            throw new IllegalArgumentException("sku and reservationId are required");
        }
        ActivitySkuStockRestoreLedger ledger = ActivitySkuStockRestoreLedger.builder()
                .sku(sku).reservationId(reservationId).status("reserved").build();
        try {
            activitySkuStockRestoreLedgerDao.insert(ledger);
        } catch (DuplicateKeyException duplicate) {
            ledger = activitySkuStockRestoreLedgerDao.queryByReservationId(reservationId);
            if (ledger == null || "applied".equals(ledger.getStatus())) {
                log.info("SKU库存补偿已落账，跳过重复恢复 sku:{} reservationId:{}", sku, reservationId);
                return;
            }
        }
        String restoreKey = "sku_stock_restore:" + reservationId;
        String stockKey = Constants.RedisKey.ACTIVITY_SKU_STOCK_COUNT_KEY + sku;
        ActivitySkuStockDecrementLedger decrement = activitySkuStockDecrementLedgerDao
                .queryByReservationId(reservationId);
        String reservationLockKey = decrement == null || decrement.getLockSurplus() == null
                ? "" : stockKey + Constants.UNDERLINE + decrement.getLockSurplus();
        // SETNX + INCR 在 Redis 侧组成一次状态迁移。若进程在更新数据库状态前宕机，
        // 重试会看到该标记，只关闭持久化账本，不会再次递增库存。
        redissonClient.getScript(StringCodec.INSTANCE).eval(RScript.Mode.READ_WRITE,
                "local result = -1; "
                        + "if redis.call('setnx', KEYS[1], '1') == 1 then "
                        + "redis.call('expire', KEYS[1], 2592000); "
                        + "result = redis.call('incr', KEYS[2]); "
                        + "end; "
                        + "if ARGV[1] ~= '' then redis.call('del', ARGV[1]); end; "
                        + "return result;",
                RScript.ReturnType.INTEGER, java.util.Arrays.asList(restoreKey, stockKey), reservationLockKey);

        if (decrement != null) {
            removePendingStockReservation(decrement);
            Boolean restored = transactionTemplate.execute(status -> {
                ActivitySkuStockDecrementLedger current = activitySkuStockDecrementLedgerDao
                        .queryByReservationId(reservationId);
                if (current == null || "released".equals(current.getStatus())) {
                    return true;
                }
                if ("applied".equals(current.getStatus())) {
                    if (raffleActivitySkuDao.restoreActivitySkuStock(sku) != 1) {
                        status.setRollbackOnly();
                        return false;
                    }
                }
                return activitySkuStockDecrementLedgerDao.updateStatusReleased(current) == 1;
            });
            if (!Boolean.TRUE.equals(restored)) {
                throw new IllegalStateException("activity SKU durable restore failed: " + reservationId);
            }
        }
        activitySkuStockRestoreLedgerDao.updateStatusApplied(ledger);
        log.info("SKU库存补偿恢复 sku:{} reservationId:{}", sku, reservationId);
    }

    private void removePendingStockReservation(ActivitySkuStockDecrementLedger decrement) {
        String cacheKey = Constants.RedisKey.ACTIVITY_SKU_COUNT_QUERY_KEY + Constants.UNDERLINE + decrement.getSku();
        ActivitySkuStockKeyVO value = ActivitySkuStockKeyVO.builder()
                .sku(decrement.getSku())
                .activityId(decrement.getActivityId())
                .lockSurplus(decrement.getLockSurplus())
                .reservationId(decrement.getReservationId())
                .build();
        RBlockingQueue<ActivitySkuStockKeyVO> blockingQueue = redisService.getBlockingQueue(cacheKey);
        RDelayedQueue<ActivitySkuStockKeyVO> delayedQueue = redisService.getDelayedQueue(blockingQueue);
        delayedQueue.remove(value);
        blockingQueue.remove(value);
        if (blockingQueue.isEmpty()) {
            redisService.removeFromSet(Constants.RedisKey.ACTIVITY_SKU_STOCK_PENDING_SET,
                    String.valueOf(decrement.getSku()));
        }
    }

    public List<Long> querySkuList() {
        return raffleActivitySkuDao.querySkuList();
    }

    /** 返回所有仍有待刷盘任务的 SKU（数据库列表 ∪ Redis 待处理集合）。 */
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

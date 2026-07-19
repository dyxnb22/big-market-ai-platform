package com.dyx.market.infrastructure.adapter.repository;

import com.dyx.market.domain.strategy.model.entity.StrategyAwardEntity;
import com.dyx.market.domain.strategy.model.valobj.StrategyAwardStockKeyVO;
import com.dyx.market.infrastructure.dao.IStrategyAwardDao;
import com.dyx.market.infrastructure.dao.IStrategyAwardStockDecrementLedgerDao;
import com.dyx.market.infrastructure.dao.po.StrategyAward;
import com.dyx.market.infrastructure.dao.po.StrategyAwardStockDecrementLedger;
import com.dyx.market.infrastructure.redis.IRedisService;
import com.dyx.market.types.common.Constants;
import com.dyx.market.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBlockingQueue;
import org.redisson.api.RDelayedQueue;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static com.dyx.market.types.enums.ResponseCode.UN_ASSEMBLED_STRATEGY_ARMORY;

/**
 * 策略奖品缓存与库存队列操作，从 {@link StrategyRepository} 拆分以降低单类复杂度。
 */
@Slf4j
@Component
public class StrategyAwardCacheSupport {

    private static final String STOCK_CONFIRM_DEDUPE_KEY_PREFIX = "stock_confirm:";
    /** Redis SETNX is an optional fast-path only; MySQL ledger is the durable truth. */
    private static final String STOCK_MYSQL_DECREMENT_KEY_PREFIX = "stock_mysql_decrement:";

    @Resource
    private IStrategyAwardDao strategyAwardDao;
    @Resource
    private IStrategyAwardStockDecrementLedgerDao strategyAwardStockDecrementLedgerDao;
    @Resource
    private IRedisService redisService;
    @Resource
    private TransactionTemplate transactionTemplate;
    @Resource
    private RedissonClient redissonClient;

    public List<StrategyAwardEntity> queryStrategyAwardList(Long strategyId) {
        // 优先从缓存获取
        String cacheKey = Constants.RedisKey.STRATEGY_AWARD_LIST_KEY + strategyId;
        List<StrategyAwardEntity> strategyAwardEntities = redisService.getValue(cacheKey);
        if (null != strategyAwardEntities && !strategyAwardEntities.isEmpty()) return strategyAwardEntities;
        // 从库中获取数据
        List<StrategyAward> strategyAwards = strategyAwardDao.queryStrategyAwardListByStrategyId(strategyId);
        strategyAwardEntities = new ArrayList<>(strategyAwards.size());
        for (StrategyAward strategyAward : strategyAwards) {
            StrategyAwardEntity strategyAwardEntity = StrategyAwardEntity.builder()
                    .strategyId(strategyAward.getStrategyId())
                    .awardId(strategyAward.getAwardId())
                    .awardTitle(strategyAward.getAwardTitle())
                    .awardSubtitle(strategyAward.getAwardSubtitle())
                    .awardCount(strategyAward.getAwardCount())
                    .awardCountSurplus(strategyAward.getAwardCountSurplus())
                    .awardRate(strategyAward.getAwardRate())
                    .sort(strategyAward.getSort())
                    .ruleModels(strategyAward.getRuleModels())
                    .build();
            strategyAwardEntities.add(strategyAwardEntity);
        }
        redisService.setValue(cacheKey, strategyAwardEntities);
        return strategyAwardEntities;
    }

    /**
     * 在 Redisson 中，当你调用 getMap 方法时，如果指定的 key 不存在，Redisson 并不会立即在 Redis 数据库中创建这个 key。相反，它会返回一个 RMap 对象的实例，这个实例是一个本地的 Java 对象，它代表了 Redis 中的一个哈希（hash）。
     * <p>
     * 当你开始使用这个 RMap 实例进行操作，比如添加键值对，那么 Redisson 会在 Redis 数据库中创建相应的 key，并将数据存储在这个 key 对应的哈希中。如果你只是获取了 RMap 实例而没有进行任何操作，那么在 Redis 数据库中是不会有任何变化的。
     * <p>
     * 简单来说，getMap 方法返回的 RMap 对象是懒加载的，只有在你实际进行操作时，Redis 数据库中的数据结构才会被创建或修改。
     */
    public <K, V> void storeStrategyAwardSearchRateTable(String key, Integer rateRange, Map<K, V> strategyAwardSearchRateTable) {
        // 1. 存储抽奖策略范围值，如10000，用于生成1000以内的随机数
        redisService.setValue(Constants.RedisKey.STRATEGY_RATE_RANGE_KEY + key, rateRange);
        // 2. 存储概率查找表 - putAll 直接覆盖已有条目，无需先删再写
        String tableCacheKey = Constants.RedisKey.STRATEGY_RATE_TABLE_KEY + key;
        Map<K, V> cacheRateTable = redisService.getMap(tableCacheKey);
        cacheRateTable.putAll(strategyAwardSearchRateTable);
    }

    public <K, V> Map<K, V> getMap(String key) {
        return redisService.getMap(Constants.RedisKey.STRATEGY_RATE_TABLE_KEY + key);
    }

    public Integer getStrategyAwardAssemble(String key, Integer rateKey) {
        return redisService.getFromMap(Constants.RedisKey.STRATEGY_RATE_TABLE_KEY + key, rateKey);
    }

    public int getRateRange(Long strategyId) {
        return getRateRange(String.valueOf(strategyId));
    }

    public int getRateRange(String key) {
        String cacheKey = Constants.RedisKey.STRATEGY_RATE_RANGE_KEY + key;
        if (!redisService.isExists(cacheKey)) {
            throw new AppException(UN_ASSEMBLED_STRATEGY_ARMORY.getCode(), cacheKey + Constants.COLON + UN_ASSEMBLED_STRATEGY_ARMORY.getInfo());
        }
        return redisService.getValue(cacheKey);
    }

    public void cacheStrategyAwardCount(String cacheKey, Integer awardCount) {
        if (redisService.isExists(cacheKey)) return;
        redisService.setAtomicLong(cacheKey, awardCount);
    }

    public Boolean subtractionAwardStock(String cacheKey) {
        return subtractionAwardStock(cacheKey, null);
    }

    /**
     * 扣减库存并加锁操作，decr和0对比，如果是incr操作就和总量对比，和总量对比可以动态添加库存
     *
     * @param cacheKey    缓存Key
     * @param endDateTime 活动结束时间
     */
    public Boolean subtractionAwardStock(String cacheKey, Date endDateTime) {
        long surplus = redisService.decr(cacheKey);
        if (surplus < 0) {
            // 库存小于0，恢复为0个
            redisService.setAtomicLong(cacheKey, 0);
            return false;
        }
        // 1. 按照cacheKey decr 后的值，如 99、98、97 和 key 组成为库存锁的key进行使用。
        // 2. 加锁为了兜底，如果后续有恢复库存，手动处理等，也不会超卖。因为所有的可用库存key，都被加锁了。
        String lockKey = cacheKey + Constants.UNDERLINE + surplus;
        Boolean lock = null != endDateTime
                ? redisService.setNx(lockKey, endDateTime.getTime() - System.currentTimeMillis() + TimeUnit.DAYS.toMillis(1), TimeUnit.MILLISECONDS)
                : redisService.setNx(lockKey);
        if (!Boolean.TRUE.equals(lock)) {
            log.warn("策略奖品库存加锁失败，回滚扣减 {}", lockKey);
            redisService.incr(cacheKey);
            return false;
        }
        return true;
    }

    /**
     * 预占奖品库存：DECR + lock，不落库队列；确认后再入队。
     */
    public StrategyAwardStockKeyVO reserveStock(Long strategyId, Integer awardId, Date endDateTime, String reservationId) {
        String cacheKey = Constants.RedisKey.STRATEGY_AWARD_COUNT_KEY + strategyId + Constants.UNDERLINE + awardId;
        if (reservationId == null || reservationId.trim().isEmpty()) {
            throw new IllegalArgumentException("reservationId is required for finite award stock");
        }
        StrategyAwardStockDecrementLedger existing = strategyAwardStockDecrementLedgerDao.queryByReservationId(reservationId);
        if (existing != null) {
            if ("released".equals(existing.getStatus())) {
                return null;
            }
            if (existing.getLockSurplus() != null) {
                return StrategyAwardStockKeyVO.builder().strategyId(existing.getStrategyId()).awardId(existing.getAwardId())
                        .reservationId(reservationId).lockSurplus(existing.getLockSurplus()).build();
            }
            strategyId = existing.getStrategyId();
            awardId = existing.getAwardId();
        }
        final Long ledgerStrategyId = strategyId;
        final Integer ledgerAwardId = awardId;
        if (existing == null) {
            transactionTemplate.execute(status -> {
                try {
                    strategyAwardStockDecrementLedgerDao.insert(StrategyAwardStockDecrementLedger.builder()
                            .reservationId(reservationId).strategyId(ledgerStrategyId).awardId(ledgerAwardId).status("reserved").build());
                } catch (DuplicateKeyException duplicate) {
                    // Another request created the durable reservation. The Redis
                    // script below is still safe to resume by reservationId.
                }
                return true;
            });
        }
        // Mockito/unit wiring can exercise the repository without a Redisson
        // client; production always supplies it. Keep the legacy branch only
        // as a compatibility fallback for that environment.
        if (redissonClient == null) {
            return reserveStockWithLegacyRedis(strategyId, awardId, endDateTime, reservationId, cacheKey);
        }
        long lockTtlMillis = endDateTime == null
                ? TimeUnit.DAYS.toMillis(30)
                : Math.max(TimeUnit.SECONDS.toMillis(1), endDateTime.getTime() - System.currentTimeMillis() + TimeUnit.DAYS.toMillis(1));
        Object scriptResult = redissonClient.getScript(StringCodec.INSTANCE).eval(RScript.Mode.READ_WRITE,
                "local saved = redis.call('get', KEYS[1]); "
                        + "if saved then return tonumber(saved); end; "
                        + "local surplus = redis.call('decr', KEYS[2]); "
                        + "if surplus < 0 then redis.call('set', KEYS[2], 0); return -1; end; "
                        + "local lockKey = KEYS[2] .. '_' .. surplus; "
                        + "local locked = redis.call('set', lockKey, '1', 'NX', 'PX', ARGV[1]); "
                        + "if not locked then redis.call('incr', KEYS[2]); return -2; end; "
                        + "redis.call('set', KEYS[1], surplus, 'EX', ARGV[2]); return surplus;",
                RScript.ReturnType.INTEGER,
                java.util.Arrays.asList("stock_reservation:" + reservationId, cacheKey),
                String.valueOf(lockTtlMillis), String.valueOf(TimeUnit.DAYS.toSeconds(30)));
        long surplus = ((Number) scriptResult).longValue();
        if (surplus < 0) {
            strategyAwardStockDecrementLedgerDao.updateStatusReleased(reservationId);
            return null;
        }
        int updated = strategyAwardStockDecrementLedgerDao.updateLockSurplus(StrategyAwardStockDecrementLedger.builder()
                .reservationId(reservationId).lockSurplus(surplus).build());
        if (updated != 1) {
            throw new IllegalStateException("strategy stock reservation durable update failed: " + reservationId);
        }
        return StrategyAwardStockKeyVO.builder()
                .strategyId(strategyId)
                .awardId(awardId)
                .reservationId(reservationId)
                .lockSurplus(surplus)
                .build();
    }

    private StrategyAwardStockKeyVO reserveStockWithLegacyRedis(Long strategyId, Integer awardId,
                                                                  Date endDateTime, String reservationId,
                                                                  String cacheKey) {
        long surplus = redisService.decr(cacheKey);
        if (surplus < 0) {
            redisService.setAtomicLong(cacheKey, 0);
            strategyAwardStockDecrementLedgerDao.updateStatusReleased(reservationId);
            return null;
        }
        String lockKey = cacheKey + Constants.UNDERLINE + surplus;
        Boolean lock = endDateTime == null
                ? redisService.setNx(lockKey)
                : redisService.setNx(lockKey, endDateTime.getTime() - System.currentTimeMillis()
                        + TimeUnit.DAYS.toMillis(1), TimeUnit.MILLISECONDS);
        if (!Boolean.TRUE.equals(lock)) {
            redisService.incr(cacheKey);
            strategyAwardStockDecrementLedgerDao.updateStatusReleased(reservationId);
            return null;
        }
        strategyAwardStockDecrementLedgerDao.updateLockSurplus(StrategyAwardStockDecrementLedger.builder()
                .reservationId(reservationId).lockSurplus(surplus).build());
        return StrategyAwardStockKeyVO.builder().strategyId(strategyId).awardId(awardId)
                .reservationId(reservationId).lockSurplus(surplus).build();
    }

    /**
     * 确认预占：中奖记录落库成功后入队，由 UpdateAwardStockJob 异步写 MySQL。
     */
    public void confirmReservation(StrategyAwardStockKeyVO reservation) {
        if (null == reservation || null == reservation.getReservationId()) {
            return;
        }
        String dedupeKey = STOCK_CONFIRM_DEDUPE_KEY_PREFIX + reservation.getReservationId();
        if (Boolean.TRUE.equals(redisService.isExists(dedupeKey))) {
            log.info("奖品库存预占确认已提交，跳过重复入队 strategyId:{} awardId:{} reservationId:{}",
                    reservation.getStrategyId(), reservation.getAwardId(), reservation.getReservationId());
            return;
        }
        try {
            awardStockConsumeSendQueue(reservation);
        } catch (Exception e) {
            log.error("奖品库存预占确认入队失败 strategyId:{} awardId:{} reservationId:{}",
                    reservation.getStrategyId(), reservation.getAwardId(), reservation.getReservationId(), e);
            throw new RuntimeException("award stock confirm enqueue failed", e);
        }
        redisService.setNx(dedupeKey, 7, TimeUnit.DAYS);
        log.info("奖品库存预占确认 strategyId:{} awardId:{} reservationId:{}",
                reservation.getStrategyId(), reservation.getAwardId(), reservation.getReservationId());
    }

    /**
     * 释放预占：抽奖落库失败时 INCR 恢复 Redis 并删除 lock key。
     */
    public void releaseReservation(StrategyAwardStockKeyVO reservation) {
        if (null == reservation) {
            return;
        }
        String cacheKey = Constants.RedisKey.STRATEGY_AWARD_COUNT_KEY + reservation.getStrategyId() + Constants.UNDERLINE + reservation.getAwardId();
        if (reservation.getReservationId() != null) {
            StrategyAwardStockDecrementLedger existing = strategyAwardStockDecrementLedgerDao
                    .queryByReservationId(reservation.getReservationId());
            if (existing != null && "released".equals(existing.getStatus())) {
                return;
            }
            if (existing != null && "applied".equals(existing.getStatus())) {
                return;
            }
            if (redissonClient == null) {
                redisService.incr(cacheKey);
                if (reservation.getLockSurplus() != null) {
                    redisService.remove(cacheKey + Constants.UNDERLINE + reservation.getLockSurplus());
                }
                if (existing != null) {
                    strategyAwardStockDecrementLedgerDao.updateStatusReleased(reservation.getReservationId());
                }
                return;
            }
            String lockKey = null == reservation.getLockSurplus()
                    ? "" : cacheKey + Constants.UNDERLINE + reservation.getLockSurplus();
            redissonClient.getScript(StringCodec.INSTANCE).eval(RScript.Mode.READ_WRITE,
                    "if redis.call('setnx', KEYS[1], '1') == 1 then "
                            + "redis.call('expire', KEYS[1], 2592000); "
                            + "redis.call('incr', KEYS[2]); "
                            + "if ARGV[1] ~= '' then redis.call('del', ARGV[1]); end; return 1; "
                            + "end; return 0;",
                    RScript.ReturnType.INTEGER,
                    java.util.Collections.singletonList("stock_release:" + reservation.getReservationId()),
                    cacheKey, lockKey);
            strategyAwardStockDecrementLedgerDao.updateStatusReleased(reservation.getReservationId());
            return;
        }
        redisService.incr(cacheKey);
        if (null != reservation.getLockSurplus()) {
            redisService.remove(cacheKey + Constants.UNDERLINE + reservation.getLockSurplus());
        }
        log.info("奖品库存预占释放 strategyId:{} awardId:{} reservationId:{}",
                reservation.getStrategyId(), reservation.getAwardId(), reservation.getReservationId());
    }

    public void awardStockConsumeSendQueue(StrategyAwardStockKeyVO strategyAwardStockKeyVO) {
        String cacheKey = Constants.RedisKey.STRATEGY_AWARD_COUNT_QUERY_KEY + Constants.UNDERLINE + strategyAwardStockKeyVO.getStrategyId() + Constants.UNDERLINE + strategyAwardStockKeyVO.getAwardId();
        RBlockingQueue<StrategyAwardStockKeyVO> blockingQueue = redisService.getBlockingQueue(cacheKey);
        RDelayedQueue<StrategyAwardStockKeyVO> delayedQueue = redisService.getDelayedQueue(blockingQueue);
        delayedQueue.offer(strategyAwardStockKeyVO, 3, TimeUnit.SECONDS);
        redisService.addToSet(Constants.RedisKey.STRATEGY_AWARD_STOCK_PENDING_SET,
                strategyAwardStockKeyVO.getStrategyId() + Constants.UNDERLINE + strategyAwardStockKeyVO.getAwardId());
    }

    public StrategyAwardStockKeyVO takeQueueValue() {
        String cacheKey = Constants.RedisKey.STRATEGY_AWARD_COUNT_QUERY_KEY;
        RBlockingQueue<StrategyAwardStockKeyVO> destinationQueue = redisService.getBlockingQueue(cacheKey);
        return destinationQueue.poll();
    }

    public StrategyAwardStockKeyVO peekQueueValue(Long strategyId, Integer awardId) {
        String cacheKey = Constants.RedisKey.STRATEGY_AWARD_COUNT_QUERY_KEY + Constants.UNDERLINE + strategyId + Constants.UNDERLINE + awardId;
        RBlockingQueue<StrategyAwardStockKeyVO> destinationQueue = redisService.getBlockingQueue(cacheKey);
        return destinationQueue.peek();
    }

    public void ackQueueValue(Long strategyId, Integer awardId) {
        String cacheKey = Constants.RedisKey.STRATEGY_AWARD_COUNT_QUERY_KEY + Constants.UNDERLINE + strategyId + Constants.UNDERLINE + awardId;
        RBlockingQueue<StrategyAwardStockKeyVO> destinationQueue = redisService.getBlockingQueue(cacheKey);
        destinationQueue.poll();
        if (destinationQueue.isEmpty()) {
            redisService.removeFromSet(Constants.RedisKey.STRATEGY_AWARD_STOCK_PENDING_SET,
                    strategyId + Constants.UNDERLINE + awardId);
        }
    }

    /**
     * DB 成功后再 ACK 队列项；失败保留队列供重试。
     */
    public void syncStrategyAwardStockFromQueue(Long strategyId, Integer awardId) {
        StrategyAwardStockKeyVO stockKey = peekQueueValue(strategyId, awardId);
        if (null == stockKey) {
            // A process can die after the durable reservation and before the
            // delayed-queue offer. Resume those reservations from MySQL so a
            // Redis-only queue is not the sole recovery record.
            for (StrategyAwardStockDecrementLedger reserved :
                    strategyAwardStockDecrementLedgerDao.queryReservedByStrategyAward(strategyId, awardId)) {
                resumeReservedStock(reserved);
            }
            redisService.removeFromSet(Constants.RedisKey.STRATEGY_AWARD_STOCK_PENDING_SET,
                    strategyId + Constants.UNDERLINE + awardId);
            return;
        }
        try {
            updateStrategyAwardStockOnce(stockKey);
            ackQueueValue(strategyId, awardId);
        } catch (Exception e) {
            log.error("奖品库存落库失败，保留队列重试 strategyId:{} awardId:{}", strategyId, awardId, e);
        }
    }

    private void resumeReservedStock(StrategyAwardStockDecrementLedger reserved) {
        if (reserved == null || reserved.getReservationId() == null) {
            return;
        }
        StrategyAwardStockKeyVO stockKey = StrategyAwardStockKeyVO.builder()
                .strategyId(reserved.getStrategyId())
                .awardId(reserved.getAwardId())
                .reservationId(reserved.getReservationId())
                .lockSurplus(reserved.getLockSurplus())
                .build();
        if (reserved.getLockSurplus() == null) {
            stockKey = reserveStock(reserved.getStrategyId(), reserved.getAwardId(), null,
                    reserved.getReservationId());
            if (stockKey == null) {
                return;
            }
        }
        updateStrategyAwardStockOnce(stockKey);
    }

    public StrategyAwardStockKeyVO takeQueueValue(Long strategyId, Integer awardId) {
        String cacheKey = Constants.RedisKey.STRATEGY_AWARD_COUNT_QUERY_KEY + Constants.UNDERLINE + strategyId + Constants.UNDERLINE + awardId;
        RBlockingQueue<StrategyAwardStockKeyVO> destinationQueue = redisService.getBlockingQueue(cacheKey);
        return destinationQueue.poll();
    }

    public void updateStrategyAwardStock(Long strategyId, Integer awardId) {
        StrategyAward strategyAward = new StrategyAward();
        strategyAward.setStrategyId(strategyId);
        strategyAward.setAwardId(awardId);
        strategyAwardDao.updateStrategyAwardStock(strategyAward);
    }

    /**
     * 按 reservationId 幂等扣减 MySQL 库存。最终事实以 ledger INSERT 为准；
     * Redis SETNX 仅作可选加速，不得单独作为「DB 已完成」证明。
     */
    public void updateStrategyAwardStockOnce(StrategyAwardStockKeyVO stockKey) {
        if (null == stockKey) {
            return;
        }
        String reservationId = stockKey.getReservationId();
        if (null == reservationId || reservationId.isEmpty()) {
            updateStrategyAwardStock(stockKey.getStrategyId(), stockKey.getAwardId());
            return;
        }

        String dedupeKey = STOCK_MYSQL_DECREMENT_KEY_PREFIX + reservationId;
        // Optional fast-path: if Redis says done, still verify MySQL ledger before skipping.
        if (!Boolean.TRUE.equals(redisService.setNx(dedupeKey, 7, TimeUnit.DAYS))) {
            StrategyAwardStockDecrementLedger existing = strategyAwardStockDecrementLedgerDao.queryByReservationId(reservationId);
            if (existing != null && !"reserved".equals(existing.getStatus())) {
                log.info("奖品库存 MySQL 扣减已落账，跳过重复 reservationId:{}", reservationId);
                return;
            }
            // Crash window: SETNX set but ledger missing — clear Redis and continue to durable path.
            redisService.remove(dedupeKey);
        }

        try {
            Boolean applied = transactionTemplate.execute(status -> {
                StrategyAwardStockDecrementLedger existing = strategyAwardStockDecrementLedgerDao
                        .queryByReservationId(reservationId);
                if (existing != null && "applied".equals(existing.getStatus())) {
                    return true;
                }
                if (existing != null && "reserved".equals(existing.getStatus())) {
                    // The status transition is the authorization for the
                    // physical decrement. A concurrent worker may have read
                    // the same reserved row and won the CAS first.
                    if (strategyAwardStockDecrementLedgerDao.updateStatusApplied(reservationId) == 1) {
                        updateStrategyAwardStock(stockKey.getStrategyId(), stockKey.getAwardId());
                    }
                    return true;
                }
                try {
                    strategyAwardStockDecrementLedgerDao.insert(StrategyAwardStockDecrementLedger.builder()
                            .reservationId(reservationId)
                            .strategyId(stockKey.getStrategyId())
                            .awardId(stockKey.getAwardId())
                            .lockSurplus(stockKey.getLockSurplus())
                            .build());
                } catch (DuplicateKeyException e) {
                    log.info("奖品库存 ledger 已存在，跳过重复 reservationId:{}", reservationId);
                    return true;
                }
                updateStrategyAwardStock(stockKey.getStrategyId(), stockKey.getAwardId());
                return true;
            });
            if (!Boolean.TRUE.equals(applied)) {
                throw new IllegalStateException("strategy award stock ledger transaction returned false");
            }
            // Best-effort Redis cache of durable success (ignore failures).
            redisService.setNx(dedupeKey, 7, TimeUnit.DAYS);
        } catch (RuntimeException e) {
            redisService.remove(dedupeKey);
            throw e;
        }
    }

    /**
     * Open-activity awards plus any pending flush keys (survives activity offline).
     */
    public List<StrategyAwardStockKeyVO> queryPendingStrategyAwardStockKeys() {
        List<StrategyAwardStockKeyVO> open = queryOpenActivityStrategyAwardList();
        Set<String> seen = new HashSet<>();
        List<StrategyAwardStockKeyVO> merged = new ArrayList<>();
        if (null != open) {
            for (StrategyAwardStockKeyVO vo : open) {
                String key = vo.getStrategyId() + Constants.UNDERLINE + vo.getAwardId();
                if (seen.add(key)) {
                    merged.add(vo);
                }
            }
        }
        Set<String> pending = redisService.getSetMembers(Constants.RedisKey.STRATEGY_AWARD_STOCK_PENDING_SET);
        if (null != pending) {
            for (String member : pending) {
                if (!seen.add(member)) {
                    continue;
                }
                String[] parts = member.split(Constants.UNDERLINE, 2);
                if (parts.length != 2) {
                    continue;
                }
                try {
                    merged.add(StrategyAwardStockKeyVO.builder()
                            .strategyId(Long.valueOf(parts[0]))
                            .awardId(Integer.valueOf(parts[1]))
                            .build());
                } catch (NumberFormatException ignored) {
                    log.warn("invalid pending strategy award stock key: {}", member);
                }
            }
        }
        List<StrategyAwardStockDecrementLedger> reserved = strategyAwardStockDecrementLedgerDao.queryAllReserved(500);
        if (reserved != null) {
            for (StrategyAwardStockDecrementLedger row : reserved) {
                String key = row.getStrategyId() + Constants.UNDERLINE + row.getAwardId();
                if (seen.add(key)) {
                    merged.add(StrategyAwardStockKeyVO.builder()
                            .strategyId(row.getStrategyId())
                            .awardId(row.getAwardId())
                            .build());
                }
            }
        }
        return merged;
    }

    public StrategyAwardEntity queryStrategyAwardEntity(Long strategyId, Integer awardId) {
        // 优先从缓存获取
        String cacheKey = Constants.RedisKey.STRATEGY_AWARD_KEY + strategyId + Constants.UNDERLINE + awardId;
        StrategyAwardEntity strategyAwardEntity = redisService.getValue(cacheKey);
        if (null != strategyAwardEntity) return strategyAwardEntity;
        // 查询数据
        StrategyAward strategyAwardReq = new StrategyAward();
        strategyAwardReq.setStrategyId(strategyId);
        strategyAwardReq.setAwardId(awardId);
        StrategyAward strategyAwardRes = strategyAwardDao.queryStrategyAward(strategyAwardReq);
        // 转换数据
        strategyAwardEntity = StrategyAwardEntity.builder()
                .strategyId(strategyAwardRes.getStrategyId())
                .awardId(strategyAwardRes.getAwardId())
                .awardTitle(strategyAwardRes.getAwardTitle())
                .awardSubtitle(strategyAwardRes.getAwardSubtitle())
                .awardCount(strategyAwardRes.getAwardCount())
                .awardCountSurplus(strategyAwardRes.getAwardCountSurplus())
                .awardRate(strategyAwardRes.getAwardRate())
                .sort(strategyAwardRes.getSort())
                .build();
        // 缓存结果
        redisService.setValue(cacheKey, strategyAwardEntity);
        // 返回数据
        return strategyAwardEntity;
    }

    public List<StrategyAwardStockKeyVO> queryOpenActivityStrategyAwardList() {
        List<StrategyAward> strategyAwards = strategyAwardDao.queryOpenActivityStrategyAwardList();
        if (null == strategyAwards || strategyAwards.isEmpty()) {
            return Collections.emptyList();
        }

        List<StrategyAwardStockKeyVO> strategyAwardStockKeyVOS = new ArrayList<>();
        for (StrategyAward strategyAward : strategyAwards) {
            StrategyAwardStockKeyVO strategyAwardStockKeyVO = StrategyAwardStockKeyVO.builder()
                    .strategyId(strategyAward.getStrategyId())
                    .awardId(strategyAward.getAwardId())
                    .build();
            strategyAwardStockKeyVOS.add(strategyAwardStockKeyVO);
        }

        return strategyAwardStockKeyVOS;
    }

    public void cacheStrategyArmoryAlgorithm(String key, String beanName) {
        String cacheKey = Constants.RedisKey.STRATEGY_ARMORY_ALGORITHM_KEY + key;
        redisService.setValue(cacheKey, beanName);
    }

    public String queryStrategyArmoryAlgorithmFromCache(String key) {
        String cacheKey = Constants.RedisKey.STRATEGY_ARMORY_ALGORITHM_KEY + key;
        if (!redisService.isExists(cacheKey)) return null;
        return redisService.getValue(cacheKey);
    }

}

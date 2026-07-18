package com.dyx.market.domain.activity.adapter.repository;

import com.dyx.market.domain.activity.model.aggregate.CreatePartakeOrderAggregate;
import com.dyx.market.domain.activity.model.aggregate.CreateQuotaOrderAggregate;
import com.dyx.market.domain.activity.model.entity.*;
import com.dyx.market.domain.activity.model.valobj.ActivitySkuStockKeyVO;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * @author Fuzhengwei bugstack.cn @小傅哥
 * @description 活动仓储接口
 * @create 2024-03-16 10:31
 */
public interface IActivityRepository {

    ActivitySkuEntity queryActivitySku(Long sku);

    ActivityEntity queryRaffleActivityByActivityId(Long activityId);

    ActivityCountEntity queryRaffleActivityCountByActivityCountId(Long activityCountId);

    void doSaveNoPayOrder(CreateQuotaOrderAggregate createOrderAggregate);

    void doSaveCreditPayOrder(CreateQuotaOrderAggregate createQuotaOrderAggregate);

    void cacheActivitySkuStockCount(String cacheKey, Integer stockCount);

    boolean subtractionActivitySkuStock(Long sku, Long activityId, String cacheKey, Date endDateTime);

    default boolean subtractionActivitySkuStock(Long sku, Long activityId, String cacheKey,
                                                Date endDateTime, String reservationId) {
        return subtractionActivitySkuStock(sku, activityId, cacheKey, endDateTime);
    }

    void activitySkuStockConsumeSendQueue(ActivitySkuStockKeyVO activitySkuStockKeyVO);

    ActivitySkuStockKeyVO takeQueueValue();

    ActivitySkuStockKeyVO takeQueueValue(Long sku);

    void clearQueueValue();

    void clearQueueValue(Long sku);

    void updateActivitySkuStock(Long sku);

    void syncActivitySkuStockFromQueue(Long sku);

    void clearActivitySkuStock(Long sku);

    /**
     * Restore (increment) the Redis SKU stock by 1.
     * Used when a SKU exchange fails after the initial stock decrement,
     * so the slot is returned to the available pool.
     */
    void restoreActivitySkuStock(Long sku);

    /** Idempotently restore one previously reserved SKU slot. */
    default void restoreActivitySkuStock(Long sku, String reservationId) {
        restoreActivitySkuStock(sku);
    }

    List<Long> querySkuList();

    UserRaffleOrderEntity queryNoUsedRaffleOrder(PartakeRaffleActivityEntity partakeRaffleActivityEntity);

    ActivityAccountEntity queryActivityAccountByUserId(String userId, Long activityId);

    ActivityAccountMonthEntity queryActivityAccountMonthByUserId(String userId, Long activityId, String month);

    ActivityAccountDayEntity queryActivityAccountDayByUserId(String userId, Long activityId, String day);

    void saveCreatePartakeOrderAggregate(CreatePartakeOrderAggregate createPartakeOrderAggregate);

    List<ActivitySkuEntity> queryActivitySkuListByActivityId(Long activityId);

    Integer queryRaffleActivityAccountDayPartakeCount(Long activityId, String userId);

    ActivityAccountEntity queryActivityAccountEntity(Long activityId, String userId);

    Integer queryRaffleActivityAccountPartakeCount(Long activityId, String userId);

    void updateOrder(DeliveryOrderEntity deliveryOrderEntity);

    UnpaidActivityOrderEntity queryUnpaidActivityOrder(SkuRechargeEntity skuRechargeEntity);

    /**
     * 按 outBusinessNo 查询配额订单（任意 state），用于返利/兑换幂等。
     */
    UnpaidActivityOrderEntity queryQuotaOrderByOutBusinessNo(String userId, String outBusinessNo);

    List<SkuProductEntity> querySkuProductEntityListByActivityId(Long activityId);

    BigDecimal queryUserCreditAccountAmount(String userId);

    void appendStageActivity(String channel, String source, Long activityId);

    void updateStageActivity2Active(Long id);

    void updateStageActivity2Expire(Long id);

    void updateRaffleActivityState(Long activityId, String state);

    Long queryStageActiveBySC(String channel, String source);

    List<RaffleActivityStageEntity> queryStageActivityList();

    Long queryStageActivity2ActiveById(Long id);

    /**
     * Atomically decrement total/month/day quota for a confirmed raffle participation,
     * guarded by an idempotency ledger row (raffle_quota_decrement_ledger).
     *
     * foundation for AccountQuotaServiceRPC.decrementQuota.
     *
     * Idempotency: a duplicate call with the same outBusinessNo returns true immediately
     * without re-decrementing (DuplicateKeyException on ledger INSERT is caught and
     * treated as "already applied").
     *
     * @return true  — quota was decremented (or already decremented for this outBusinessNo)
     *         false — quota exhausted; raffle must be rejected
     */
    boolean decrementQuotaWithLedger(String userId, Long activityId, String outBusinessNo);

    /**
     * Atomically roll back a previously decremented quota slot (saga compensation).
     *
     * ledger-guarded rollback.
     *   - No ledger row         → safe no-op, return true
     *   - status = rolled_back  → idempotent, return true
     *   - status = applied      → CAS applied→rolled_back + restore total/month/day surplus
     *
     * @return true in all safe cases; caller should log but not retry on false
     */
    boolean rollbackQuotaWithLedger(String userId, Long activityId, String outBusinessNo);

    /**
     * Persist only the raffle participation order row without touching quota accounts.
     *
     * used in the flag=true (remote quota decrement) path where quota
     * has already been decremented via IActivityAccountPort.decrementQuota before this
     * call. Shard-routed by userId, same as saveCreatePartakeOrderAggregate.
     */
    void savePartakeOrderOnly(CreatePartakeOrderAggregate createPartakeOrderAggregate);

    /**
     * Mark a create-state raffle order as failed. Returns false when the order has
     * already moved out of create state, allowing callers to avoid duplicate rollback.
     */
    boolean markRaffleOrderFailed(String userId, String orderId);

    /**
     * Compensate (restore) a draw quota for a just-failed raffle order. The order is
     * first moved create -> failed by CAS; quota is restored only when that state
     * transition succeeds, so repeated compensation attempts are ignored.
     *
     * @param orderTime the original order creation time — used to derive the correct
     *                  month/day account keys so cross-day/-month compensation is accurate.
     */
    void compensatePartakeQuota(String userId, Long activityId, String orderId, Date orderTime);

}

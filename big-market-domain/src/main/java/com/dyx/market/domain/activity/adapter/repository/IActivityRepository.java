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
     * 将 Redis 中的 SKU 库存递增 1。
     * 用于 SKU 兑换在首次扣减库存后失败的场景，把未完成的库存槽位放回可用池。
     */
    void restoreActivitySkuStock(Long sku);

    /** 按 reservationId 幂等恢复一笔已经预占的 SKU 库存槽位。 */
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
     * 在一次事务内扣减已确认抽奖参与的总/月/日额度，并由
     * raffle_quota_decrement_ledger 账本提供幂等保护。
     *
     * 该方法是 AccountQuotaServiceRPC.decrementQuota 的本地实现基础。
     * 使用相同 outBusinessNo 重复调用时，账本唯一键冲突会被视为“已经扣减”，直接返回 true，
     * 不会再次消费额度。
     *
     * @return true 表示额度已扣减或该 outBusinessNo 已经扣减；false 表示额度不足，应拒绝抽奖
     */
    boolean decrementQuotaWithLedger(String userId, Long activityId, String outBusinessNo);

    /**
     * 原子回滚此前扣减的一次额度（Saga 补偿）。
     *
     * 回滚由账本状态保护：没有账本行时安全空操作并返回 true；状态已是 rolled_back 时幂等返回 true；
     * 状态为 applied 时通过 CAS 将 applied 迁移为 rolled_back，并恢复总/月/日剩余量。
     *
     * @return 所有安全场景均返回 true；返回 false 时调用方应记录日志，但不能盲目重复补偿
     */
    boolean rollbackQuotaWithLedger(String userId, Long activityId, String outBusinessNo);

    /**
     * 只持久化抽奖参与订单，不修改本地额度账户。
     *
     * 用于远程额度扣减路径：调用本方法前，额度已经通过
     * IActivityAccountPort.decrementQuota 在 account-service 中扣减完成。
     * 订单仍按 userId 路由到用户分片，与 saveCreatePartakeOrderAggregate 一致。
     */
    void savePartakeOrderOnly(CreatePartakeOrderAggregate createPartakeOrderAggregate);

    /**
     * 将 create 状态的抽奖订单标记为 failed。
     * 订单已经离开 create 状态时返回 false，调用方据此避免重复回滚额度。
     */
    boolean markRaffleOrderFailed(String userId, String orderId);

    /**
     * 为刚刚失败的抽奖订单补偿恢复额度。
     * 先通过 CAS 将订单从 create 迁移为 failed，只有状态迁移成功才恢复额度，
     * 因此重复补偿会被安全忽略。
     *
     * @param orderTime 原始下单时间，用于计算正确的月/日账户键，保证跨日或跨月补偿准确
     */
    void compensatePartakeQuota(String userId, Long activityId, String orderId, Date orderTime);

}

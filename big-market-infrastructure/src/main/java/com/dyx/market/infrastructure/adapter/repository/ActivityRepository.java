package com.dyx.market.infrastructure.adapter.repository;

import com.dyx.market.domain.activity.adapter.port.IActivityAccountPort;
import com.dyx.market.domain.activity.model.aggregate.CreatePartakeOrderAggregate;
import com.dyx.market.domain.activity.model.aggregate.CreateQuotaOrderAggregate;
import com.dyx.market.domain.activity.model.entity.*;
import com.dyx.market.domain.activity.model.valobj.ActivitySkuStockKeyVO;
import com.dyx.market.domain.activity.adapter.repository.IActivityRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * @author Fuzhengwei bugstack.cn @小傅哥
 * @description 活动仓储服务
 * @create 2024-03-16 11:03
 */
@Slf4j
@Repository
public class ActivityRepository implements IActivityRepository {

    @Lazy
    @Resource
    private IActivityAccountPort activityAccountPort;
    @Resource
    private ActivitySkuStockCacheSupport activitySkuStockCacheSupport;
    @Resource
    private ActivityStageRepositorySupport activityStageRepositorySupport;
    @Resource
    private ActivityQuotaLedgerSupport activityQuotaLedgerSupport;
    @Resource
    private ActivityPartakeOrderSupport activityPartakeOrderSupport;
    @Resource
    private ActivityQuotaOrderSupport activityQuotaOrderSupport;
    @Resource
    private ActivityQuerySupport activityQuerySupport;

    @Override
    public ActivitySkuEntity queryActivitySku(Long sku) {
        return activityQuerySupport.queryActivitySku(sku);
    }

    @Override
    public ActivityEntity queryRaffleActivityByActivityId(Long activityId) {
        return activityQuerySupport.queryRaffleActivityByActivityId(activityId);
    }

    @Override
    public ActivityCountEntity queryRaffleActivityCountByActivityCountId(Long activityCountId) {
        return activityQuerySupport.queryRaffleActivityCountByActivityCountId(activityCountId);
    }

    @Override
    public void doSaveNoPayOrder(CreateQuotaOrderAggregate createOrderAggregate) {
        activityQuotaOrderSupport.doSaveNoPayOrder(createOrderAggregate);
    }

    @Override
    public void doSaveCreditPayOrder(CreateQuotaOrderAggregate createOrderAggregate) {
        activityQuotaOrderSupport.doSaveCreditPayOrder(createOrderAggregate);
    }

    @Override
    public void cacheActivitySkuStockCount(String cacheKey, Integer stockCount) {
        activitySkuStockCacheSupport.cacheActivitySkuStockCount(cacheKey, stockCount);
    }

    @Override
    public boolean subtractionActivitySkuStock(Long sku, String cacheKey, Date endDateTime) {
        return activitySkuStockCacheSupport.subtractionActivitySkuStock(sku, cacheKey, endDateTime);
    }

    @Override
    public void activitySkuStockConsumeSendQueue(ActivitySkuStockKeyVO activitySkuStockKeyVO) {
        activitySkuStockCacheSupport.activitySkuStockConsumeSendQueue(activitySkuStockKeyVO);
    }

    /**
     * @deprecated 请使用 {@link #takeQueueValue(Long)} 按 SKU 消费库存队列。
     */
    @Deprecated
    @SuppressWarnings("java:S1133")
    @Override
    public ActivitySkuStockKeyVO takeQueueValue() {
        return activitySkuStockCacheSupport.takeQueueValue();
    }

    @Override
    public ActivitySkuStockKeyVO takeQueueValue(Long sku) {
        return activitySkuStockCacheSupport.takeQueueValue(sku);
    }

    /**
     * @deprecated 请使用 {@link #clearQueueValue(Long)} 按 SKU 清理库存队列。
     */
    @Deprecated
    @SuppressWarnings("java:S1133")
    @Override
    public void clearQueueValue() {
        activitySkuStockCacheSupport.clearQueueValue();
    }

    @Override
    public void clearQueueValue(Long sku) {
        activitySkuStockCacheSupport.clearQueueValue(sku);
    }

    @Override
    public void updateActivitySkuStock(Long sku) {
        activitySkuStockCacheSupport.updateActivitySkuStock(sku);
    }

    @Override
    public void clearActivitySkuStock(Long sku) {
        activitySkuStockCacheSupport.clearActivitySkuStock(sku);
    }

    @Override
    public void restoreActivitySkuStock(Long sku) {
        activitySkuStockCacheSupport.restoreActivitySkuStock(sku);
    }

    @Override
    public List<Long> querySkuList() {
        return activitySkuStockCacheSupport.querySkuList();
    }

    @Override
    public UserRaffleOrderEntity queryNoUsedRaffleOrder(PartakeRaffleActivityEntity partakeRaffleActivityEntity) {
        return activityQuerySupport.queryNoUsedRaffleOrder(partakeRaffleActivityEntity);
    }

    @Override
    public ActivityAccountEntity queryActivityAccountByUserId(String userId, Long activityId) {
        return activityQuerySupport.queryActivityAccountByUserId(userId, activityId);
    }

    @Override
    public ActivityAccountMonthEntity queryActivityAccountMonthByUserId(String userId, Long activityId, String month) {
        return activityQuerySupport.queryActivityAccountMonthByUserId(userId, activityId, month);
    }

    @Override
    public ActivityAccountDayEntity queryActivityAccountDayByUserId(String userId, Long activityId, String day) {
        return activityQuerySupport.queryActivityAccountDayByUserId(userId, activityId, day);
    }

    @Override
    public void saveCreatePartakeOrderAggregate(CreatePartakeOrderAggregate createPartakeOrderAggregate) {
        activityPartakeOrderSupport.saveCreatePartakeOrderAggregate(createPartakeOrderAggregate);
    }

    @Override
    public List<ActivitySkuEntity> queryActivitySkuListByActivityId(Long activityId) {
        return activityQuerySupport.queryActivitySkuListByActivityId(activityId);
    }

    @Override
    public Integer queryRaffleActivityAccountDayPartakeCount(Long activityId, String userId) {
        return activityQuerySupport.queryRaffleActivityAccountDayPartakeCount(activityId, userId);
    }

    @Override
    public ActivityAccountEntity queryActivityAccountEntity(Long activityId, String userId) {
        return activityQuerySupport.queryActivityAccountEntity(activityId, userId);
    }

    @Override
    public Integer queryRaffleActivityAccountPartakeCount(Long activityId, String userId) {
        return activityQuerySupport.queryRaffleActivityAccountPartakeCount(activityId, userId);
    }

    @Override
    public void updateOrder(DeliveryOrderEntity deliveryOrderEntity) {
        activityQuotaOrderSupport.updateOrder(deliveryOrderEntity);
    }

    @Override
    public UnpaidActivityOrderEntity queryUnpaidActivityOrder(SkuRechargeEntity skuRechargeEntity) {
        return activityQuerySupport.queryUnpaidActivityOrder(skuRechargeEntity);
    }

    @Override
    public List<SkuProductEntity> querySkuProductEntityListByActivityId(Long activityId) {
        return activityQuerySupport.querySkuProductEntityListByActivityId(activityId);
    }

    @Override
    public BigDecimal queryUserCreditAccountAmount(String userId) {
        return activityAccountPort.queryUserCreditAccountAmount(userId);
    }

    @Override
    public void appendStageActivity(String channel, String source, Long activityId) {
        activityStageRepositorySupport.appendStageActivity(channel, source, activityId);
    }

    @Override
    public void updateStageActivity2Active(Long id) {
        activityStageRepositorySupport.updateStageActivity2Active(id);
    }

    @Override
    public void updateStageActivity2Expire(Long id) {
        activityStageRepositorySupport.updateStageActivity2Expire(id);
    }

    @Override
    public void updateRaffleActivityState(Long activityId, String state) {
        activityQuerySupport.updateRaffleActivityState(activityId, state);
    }

    @Override
    public Long queryStageActiveBySC(String channel, String source) {
        return activityStageRepositorySupport.queryStageActiveBySC(channel, source);
    }

    @Override
    public List<RaffleActivityStageEntity> queryStageActivityList() {
        return activityStageRepositorySupport.queryStageActivityList();
    }

    @Override
    public Long queryStageActivity2ActiveById(Long id) {
        return activityStageRepositorySupport.queryStageActivity2ActiveById(id);
    }

    @Override
    public boolean decrementQuotaWithLedger(String userId, Long activityId, String outBusinessNo) {
        return activityQuotaLedgerSupport.decrementQuotaWithLedger(userId, activityId, outBusinessNo);
    }

    @Override
    public boolean rollbackQuotaWithLedger(String userId, Long activityId, String outBusinessNo) {
        return activityQuotaLedgerSupport.rollbackQuotaWithLedger(userId, activityId, outBusinessNo);
    }

    @Override
    public boolean markRaffleOrderFailed(String userId, String orderId) {
        return activityQuotaLedgerSupport.markRaffleOrderFailed(userId, orderId);
    }

    @Override
    public void compensatePartakeQuota(String userId, Long activityId, String orderId, Date orderTime) {
        activityQuotaLedgerSupport.compensatePartakeQuota(userId, activityId, orderId, orderTime);
    }

    @Override
    public void savePartakeOrderOnly(CreatePartakeOrderAggregate createPartakeOrderAggregate) {
        activityQuotaLedgerSupport.savePartakeOrderOnly(createPartakeOrderAggregate);
    }
}

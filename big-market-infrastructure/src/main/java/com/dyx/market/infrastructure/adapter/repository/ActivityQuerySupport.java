package com.dyx.market.infrastructure.adapter.repository;

import com.dyx.market.domain.activity.model.entity.*;
import com.dyx.market.domain.activity.model.valobj.ActivityStateVO;
import com.dyx.market.domain.activity.model.valobj.UserRaffleOrderStateVO;
import com.dyx.market.infrastructure.dao.*;
import com.dyx.market.infrastructure.dao.po.*;
import com.dyx.market.infrastructure.redis.IRedisService;
import com.dyx.market.middleware.db.router.strategy.IDBRouterStrategy;
import com.dyx.market.types.common.Constants;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

/**
 * 活动查询操作，从 {@link ActivityRepository} 拆分以降低单类复杂度。
 */
@Component
public class ActivityQuerySupport {

    @Resource
    private IRedisService redisService;
    @Resource
    private IRaffleActivityDao raffleActivityDao;
    @Resource
    private IRaffleActivitySkuDao raffleActivitySkuDao;
    @Resource
    private IRaffleActivityCountDao raffleActivityCountDao;
    @Resource
    private IRaffleActivityOrderDao raffleActivityOrderDao;
    @Resource
    private IRaffleActivityAccountDao raffleActivityAccountDao;
    @Resource
    private IRaffleActivityAccountMonthDao raffleActivityAccountMonthDao;
    @Resource
    private IRaffleActivityAccountDayDao raffleActivityAccountDayDao;
    @Resource
    private IUserRaffleOrderDao userRaffleOrderDao;
    @Resource
    private IDBRouterStrategy dbRouter;

    public ActivitySkuEntity queryActivitySku(Long sku) {
        RaffleActivitySku raffleActivitySku = raffleActivitySkuDao.queryActivitySku(sku);
        String cacheKey = Constants.RedisKey.ACTIVITY_SKU_STOCK_COUNT_KEY + sku;
        Long cacheSkuStock = redisService.getAtomicLong(cacheKey);
        if (null == cacheSkuStock) {
            // Redis key was evicted or not yet initialized — restore from DB surplus
            redisService.setAtomicLong(cacheKey, raffleActivitySku.getStockCountSurplus());
            cacheSkuStock = raffleActivitySku.getStockCountSurplus().longValue();
        }
        return ActivitySkuEntity.builder()
                .sku(raffleActivitySku.getSku())
                .activityId(raffleActivitySku.getActivityId())
                .activityCountId(raffleActivitySku.getActivityCountId())
                .stockCount(raffleActivitySku.getStockCount())
                .stockCountSurplus(cacheSkuStock.intValue())
                .productAmount(raffleActivitySku.getProductAmount())
                .build();
    }

    public ActivityEntity queryRaffleActivityByActivityId(Long activityId) {
        // 优先从缓存获取
        String cacheKey = Constants.RedisKey.ACTIVITY_KEY + activityId;
        ActivityEntity activityEntity = redisService.getValue(cacheKey);
        if (null != activityEntity) return activityEntity;
        // 从库中获取数据
        RaffleActivity raffleActivity = raffleActivityDao.queryRaffleActivityByActivityId(activityId);
        activityEntity = ActivityEntity.builder()
                .activityId(raffleActivity.getActivityId())
                .activityName(raffleActivity.getActivityName())
                .activityDesc(raffleActivity.getActivityDesc())
                .beginDateTime(raffleActivity.getBeginDateTime())
                .endDateTime(raffleActivity.getEndDateTime())
                .strategyId(raffleActivity.getStrategyId())
                .state(ActivityStateVO.valueOf(raffleActivity.getState()))
                .build();
        redisService.setValue(cacheKey, activityEntity);
        return activityEntity;
    }

    public ActivityCountEntity queryRaffleActivityCountByActivityCountId(Long activityCountId) {
        // 优先从缓存获取
        String cacheKey = Constants.RedisKey.ACTIVITY_COUNT_KEY + activityCountId;
        ActivityCountEntity activityCountEntity = redisService.getValue(cacheKey);
        if (null != activityCountEntity) return activityCountEntity;
        // 从库中获取数据
        RaffleActivityCount raffleActivityCount = raffleActivityCountDao.queryRaffleActivityCountByActivityCountId(activityCountId);
        activityCountEntity = ActivityCountEntity.builder()
                .activityCountId(raffleActivityCount.getActivityCountId())
                .totalCount(raffleActivityCount.getTotalCount())
                .dayCount(raffleActivityCount.getDayCount())
                .monthCount(raffleActivityCount.getMonthCount())
                .build();
        redisService.setValue(cacheKey, activityCountEntity);
        return activityCountEntity;
    }

    public UserRaffleOrderEntity queryNoUsedRaffleOrder(PartakeRaffleActivityEntity partakeRaffleActivityEntity) {
        try {
            dbRouter.doRouter(partakeRaffleActivityEntity.getUserId());
            // 查询数据
            UserRaffleOrder userRaffleOrderReq = new UserRaffleOrder();
            userRaffleOrderReq.setUserId(partakeRaffleActivityEntity.getUserId());
            userRaffleOrderReq.setActivityId(partakeRaffleActivityEntity.getActivityId());
            UserRaffleOrder userRaffleOrderRes = userRaffleOrderDao.queryNoUsedRaffleOrder(userRaffleOrderReq);
            if (null == userRaffleOrderRes) return null;
            return UserRaffleOrderEntity.builder()
                    .userId(userRaffleOrderRes.getUserId())
                    .activityId(userRaffleOrderRes.getActivityId())
                    .activityName(userRaffleOrderRes.getActivityName())
                    .strategyId(userRaffleOrderRes.getStrategyId())
                    .orderId(userRaffleOrderRes.getOrderId())
                    .orderTime(userRaffleOrderRes.getOrderTime())
                    .orderState(UserRaffleOrderStateVO.valueOf(userRaffleOrderRes.getOrderState()))
                    .build();
        } finally {
            dbRouter.clear();
        }
    }

    public ActivityAccountEntity queryActivityAccountByUserId(String userId, Long activityId) {
        try {
            dbRouter.doRouter(userId);
            // 1. 查询账户
            RaffleActivityAccount raffleActivityAccountReq = new RaffleActivityAccount();
            raffleActivityAccountReq.setUserId(userId);
            raffleActivityAccountReq.setActivityId(activityId);
            RaffleActivityAccount raffleActivityAccountRes = raffleActivityAccountDao.queryActivityAccountByUserId(raffleActivityAccountReq);
            if (null == raffleActivityAccountRes) return null;
            // 2. 转换对象
            return ActivityAccountEntity.builder()
                    .userId(raffleActivityAccountRes.getUserId())
                    .activityId(raffleActivityAccountRes.getActivityId())
                    .totalCount(raffleActivityAccountRes.getTotalCount())
                    .totalCountSurplus(raffleActivityAccountRes.getTotalCountSurplus())
                    .dayCount(raffleActivityAccountRes.getDayCount())
                    .dayCountSurplus(raffleActivityAccountRes.getDayCountSurplus())
                    .monthCount(raffleActivityAccountRes.getMonthCount())
                    .monthCountSurplus(raffleActivityAccountRes.getMonthCountSurplus())
                    .build();
        } finally {
            dbRouter.clear();
        }
    }

    public ActivityAccountMonthEntity queryActivityAccountMonthByUserId(String userId, Long activityId, String month) {
        try {
            dbRouter.doRouter(userId);
            // 1. 查询账户
            RaffleActivityAccountMonth raffleActivityAccountMonthReq = new RaffleActivityAccountMonth();
            raffleActivityAccountMonthReq.setUserId(userId);
            raffleActivityAccountMonthReq.setActivityId(activityId);
            raffleActivityAccountMonthReq.setMonth(month);
            RaffleActivityAccountMonth raffleActivityAccountMonthRes = raffleActivityAccountMonthDao.queryActivityAccountMonthByUserId(raffleActivityAccountMonthReq);
            if (null == raffleActivityAccountMonthRes) return null;
            // 2. 转换对象
            return ActivityAccountMonthEntity.builder()
                    .userId(raffleActivityAccountMonthRes.getUserId())
                    .activityId(raffleActivityAccountMonthRes.getActivityId())
                    .month(raffleActivityAccountMonthRes.getMonth())
                    .monthCount(raffleActivityAccountMonthRes.getMonthCount())
                    .monthCountSurplus(raffleActivityAccountMonthRes.getMonthCountSurplus())
                    .build();
        } finally {
            dbRouter.clear();
        }
    }

    public ActivityAccountDayEntity queryActivityAccountDayByUserId(String userId, Long activityId, String day) {
        try {
            dbRouter.doRouter(userId);
            // 1. 查询账户
            RaffleActivityAccountDay raffleActivityAccountDayReq = new RaffleActivityAccountDay();
            raffleActivityAccountDayReq.setUserId(userId);
            raffleActivityAccountDayReq.setActivityId(activityId);
            raffleActivityAccountDayReq.setDay(day);
            RaffleActivityAccountDay raffleActivityAccountDayRes = raffleActivityAccountDayDao.queryActivityAccountDayByUserId(raffleActivityAccountDayReq);
            if (null == raffleActivityAccountDayRes) return null;
            // 2. 转换对象
            return ActivityAccountDayEntity.builder()
                    .userId(raffleActivityAccountDayRes.getUserId())
                    .activityId(raffleActivityAccountDayRes.getActivityId())
                    .day(raffleActivityAccountDayRes.getDay())
                    .dayCount(raffleActivityAccountDayRes.getDayCount())
                    .dayCountSurplus(raffleActivityAccountDayRes.getDayCountSurplus())
                    .build();
        } finally {
            dbRouter.clear();
        }
    }

    public List<ActivitySkuEntity> queryActivitySkuListByActivityId(Long activityId) {
        List<RaffleActivitySku> raffleActivitySkus = raffleActivitySkuDao.queryActivitySkuListByActivityId(activityId);
        List<ActivitySkuEntity> activitySkuEntities = new ArrayList<>(raffleActivitySkus.size());
        for (RaffleActivitySku raffleActivitySku : raffleActivitySkus) {
            activitySkuEntities.add(ActivitySkuEntity.builder()
                    .sku(raffleActivitySku.getSku())
                    .activityCountId(raffleActivitySku.getActivityCountId())
                    .stockCount(raffleActivitySku.getStockCount())
                    .stockCountSurplus(raffleActivitySku.getStockCountSurplus())
                    .productAmount(raffleActivitySku.getProductAmount())
                    .build());
        }
        return activitySkuEntities;
    }

    public Integer queryRaffleActivityAccountDayPartakeCount(Long activityId, String userId) {
        try {
            dbRouter.doRouter(userId);
            RaffleActivityAccountDay raffleActivityAccountDay = new RaffleActivityAccountDay();
            raffleActivityAccountDay.setActivityId(activityId);
            raffleActivityAccountDay.setUserId(userId);
            raffleActivityAccountDay.setDay(RaffleActivityAccountDay.currentDay());
            Integer dayPartakeCount = raffleActivityAccountDayDao.queryRaffleActivityAccountDayPartakeCount(raffleActivityAccountDay);
            // 当日未参与抽奖则为0次
            return null == dayPartakeCount ? 0 : dayPartakeCount;
        } finally {
            dbRouter.clear();
        }
    }

    public ActivityAccountEntity queryActivityAccountEntity(Long activityId, String userId) {
        try {
            dbRouter.doRouter(userId);
            // 1. 查询总账户额度
            RaffleActivityAccount raffleActivityAccount = raffleActivityAccountDao.queryActivityAccountByUserId(RaffleActivityAccount.builder()
                    .activityId(activityId)
                    .userId(userId)
                    .build());

            if (null == raffleActivityAccount) {
                return ActivityAccountEntity.builder()
                        .activityId(activityId)
                        .userId(userId)
                        .totalCount(0)
                        .totalCountSurplus(0)
                        .monthCount(0)
                        .monthCountSurplus(0)
                        .dayCount(0)
                        .dayCountSurplus(0)
                        .build();
            }

            // 2. 查询月账户额度
            RaffleActivityAccountMonth raffleActivityAccountMonth = raffleActivityAccountMonthDao.queryActivityAccountMonthByUserId(RaffleActivityAccountMonth.builder()
                    .activityId(activityId)
                    .userId(userId)
                    .month(RaffleActivityAccountMonth.currentMonth())
                    .build());

            // 3. 查询日账户额度
            RaffleActivityAccountDay raffleActivityAccountDay = raffleActivityAccountDayDao.queryActivityAccountDayByUserId(RaffleActivityAccountDay.builder()
                    .activityId(activityId)
                    .userId(userId)
                    .day(RaffleActivityAccountDay.currentDay())
                    .build());

            // 组装对象
            ActivityAccountEntity activityAccountEntity = new ActivityAccountEntity();
            activityAccountEntity.setUserId(userId);
            activityAccountEntity.setActivityId(activityId);
            activityAccountEntity.setTotalCount(raffleActivityAccount.getTotalCount());
            activityAccountEntity.setTotalCountSurplus(raffleActivityAccount.getTotalCountSurplus());

            // 如果没有创建日账户，则从总账户中获取日总额度填充。「当新创建日账户时，会获得总账户额度」
            if (null == raffleActivityAccountDay) {
                activityAccountEntity.setDayCount(raffleActivityAccount.getDayCount());
                activityAccountEntity.setDayCountSurplus(raffleActivityAccount.getDayCount());
            } else {
                activityAccountEntity.setDayCount(raffleActivityAccountDay.getDayCount());
                activityAccountEntity.setDayCountSurplus(raffleActivityAccountDay.getDayCountSurplus());
            }

            // 如果没有创建月账户，则从总账户中获取月总额度填充。「当新创建日账户时，会获得总账户额度」
            if (null == raffleActivityAccountMonth) {
                activityAccountEntity.setMonthCount(raffleActivityAccount.getMonthCount());
                activityAccountEntity.setMonthCountSurplus(raffleActivityAccount.getMonthCount());
            } else {
                activityAccountEntity.setMonthCount(raffleActivityAccountMonth.getMonthCount());
                activityAccountEntity.setMonthCountSurplus(raffleActivityAccountMonth.getMonthCountSurplus());
            }

            return activityAccountEntity;
        } finally {
            dbRouter.clear();
        }
    }

    public Integer queryRaffleActivityAccountPartakeCount(Long activityId, String userId) {
        try {
            dbRouter.doRouter(userId);
            RaffleActivityAccount raffleActivityAccount = raffleActivityAccountDao.queryActivityAccountByUserId(RaffleActivityAccount.builder()
                    .activityId(activityId)
                    .userId(userId)
                    .build());
            if (null == raffleActivityAccount) return 0;
            return raffleActivityAccount.getTotalCount() - raffleActivityAccount.getTotalCountSurplus();
        } finally {
            dbRouter.clear();
        }
    }

    public UnpaidActivityOrderEntity queryUnpaidActivityOrder(SkuRechargeEntity skuRechargeEntity) {
        RaffleActivityOrder raffleActivityOrderReq = new RaffleActivityOrder();
        raffleActivityOrderReq.setUserId(skuRechargeEntity.getUserId());
        raffleActivityOrderReq.setSku(skuRechargeEntity.getSku());
        try {
            dbRouter.doRouter(skuRechargeEntity.getUserId());
            RaffleActivityOrder raffleActivityOrderRes = raffleActivityOrderDao.queryUnpaidActivityOrder(raffleActivityOrderReq);
            if (null == raffleActivityOrderRes) return null;
            return UnpaidActivityOrderEntity.builder()
                    .userId(raffleActivityOrderRes.getUserId())
                    .orderId(raffleActivityOrderRes.getOrderId())
                    .outBusinessNo(raffleActivityOrderRes.getOutBusinessNo())
                    .payAmount(raffleActivityOrderRes.getPayAmount())
                    .build();
        } finally {
            dbRouter.clear();
        }
    }

    public List<SkuProductEntity> querySkuProductEntityListByActivityId(Long activityId) {
        List<RaffleActivitySku> raffleActivitySkus = raffleActivitySkuDao.queryActivitySkuListByActivityId(activityId);
        List<SkuProductEntity> skuProductEntities = new ArrayList<>(raffleActivitySkus.size());
        for (RaffleActivitySku raffleActivitySku : raffleActivitySkus) {
            RaffleActivityCount raffleActivityCount = raffleActivityCountDao.queryRaffleActivityCountByActivityCountId(raffleActivitySku.getActivityCountId());

            SkuProductEntity.ActivityCount activityCount = new SkuProductEntity.ActivityCount();
            activityCount.setTotalCount(raffleActivityCount.getTotalCount());
            activityCount.setMonthCount(raffleActivityCount.getMonthCount());
            activityCount.setDayCount(raffleActivityCount.getDayCount());

            skuProductEntities.add(SkuProductEntity.builder()
                    .sku(raffleActivitySku.getSku())
                    .activityId(raffleActivitySku.getActivityId())
                    .activityCountId(raffleActivitySku.getActivityCountId())
                    .stockCount(raffleActivitySku.getStockCount())
                    .stockCountSurplus(raffleActivitySku.getStockCountSurplus())
                    .productAmount(raffleActivitySku.getProductAmount())
                    .activityCount(activityCount)
                    .build());

        }
        return skuProductEntities;
    }

    public void updateRaffleActivityState(Long activityId, String state) {
        RaffleActivity req = new RaffleActivity();
        req.setActivityId(activityId);
        req.setState(state);
        raffleActivityDao.updateRaffleActivityStateByActivityId(req);
        redisService.remove(Constants.RedisKey.ACTIVITY_KEY + activityId);
    }

}

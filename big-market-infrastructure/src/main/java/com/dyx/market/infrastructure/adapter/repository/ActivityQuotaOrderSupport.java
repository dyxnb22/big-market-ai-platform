package com.dyx.market.infrastructure.adapter.repository;

import com.dyx.market.domain.activity.model.aggregate.CreateQuotaOrderAggregate;
import com.dyx.market.domain.activity.model.entity.ActivityOrderEntity;
import com.dyx.market.domain.activity.model.entity.DeliveryOrderEntity;
import com.dyx.market.infrastructure.dao.*;
import com.dyx.market.infrastructure.dao.po.*;
import com.dyx.market.infrastructure.redis.IRedisService;
import com.dyx.market.middleware.db.router.strategy.IDBRouterStrategy;
import com.dyx.market.types.common.Constants;
import com.dyx.market.types.enums.ResponseCode;
import com.dyx.market.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.Resource;

/**
 * 活动额度充值订单：返利免支付入账、积分支付下单、支付完成更新账户。
 */
@Slf4j
@Component
public class ActivityQuotaOrderSupport {

    @Resource
    private IRedisService redisService;
    @Resource
    private IRaffleActivityOrderDao raffleActivityOrderDao;
    @Resource
    private IRaffleActivityAccountDao raffleActivityAccountDao;
    @Resource
    private IRaffleActivityAccountMonthDao raffleActivityAccountMonthDao;
    @Resource
    private IRaffleActivityAccountDayDao raffleActivityAccountDayDao;
    @Resource
    private TransactionTemplate transactionTemplate;
    @Resource
    private IDBRouterStrategy dbRouter;

    private String quotaOrderLockKey(CreateQuotaOrderAggregate agg) {
        return Constants.RedisKey.ACTIVITY_ACCOUNT_LOCK + agg.getUserId() + Constants.UNDERLINE + agg.getActivityId();
    }

    public void doSaveNoPayOrder(CreateQuotaOrderAggregate createOrderAggregate) {
        RLock lock = redisService.getLock(quotaOrderLockKey(createOrderAggregate));
        lock.lock();
        try {
            ActivityOrderEntity activityOrderEntity = createOrderAggregate.getActivityOrderEntity();
            RaffleActivityOrder raffleActivityOrder = buildRaffleActivityOrder(createOrderAggregate, activityOrderEntity);

            RaffleActivityAccount raffleActivityAccount = new RaffleActivityAccount();
            raffleActivityAccount.setUserId(createOrderAggregate.getUserId());
            raffleActivityAccount.setActivityId(createOrderAggregate.getActivityId());
            raffleActivityAccount.setTotalCount(createOrderAggregate.getTotalCount());
            raffleActivityAccount.setTotalCountSurplus(createOrderAggregate.getTotalCount());
            raffleActivityAccount.setDayCount(createOrderAggregate.getDayCount());
            raffleActivityAccount.setDayCountSurplus(createOrderAggregate.getDayCount());
            raffleActivityAccount.setMonthCount(createOrderAggregate.getMonthCount());
            raffleActivityAccount.setMonthCountSurplus(createOrderAggregate.getMonthCount());

            RaffleActivityAccountMonth raffleActivityAccountMonth = new RaffleActivityAccountMonth();
            raffleActivityAccountMonth.setUserId(createOrderAggregate.getUserId());
            raffleActivityAccountMonth.setActivityId(createOrderAggregate.getActivityId());
            raffleActivityAccountMonth.setMonth(RaffleActivityAccountMonth.currentMonth());
            raffleActivityAccountMonth.setMonthCount(createOrderAggregate.getMonthCount());
            raffleActivityAccountMonth.setMonthCountSurplus(createOrderAggregate.getMonthCount());

            RaffleActivityAccountDay raffleActivityAccountDay = new RaffleActivityAccountDay();
            raffleActivityAccountDay.setUserId(createOrderAggregate.getUserId());
            raffleActivityAccountDay.setActivityId(createOrderAggregate.getActivityId());
            raffleActivityAccountDay.setDay(RaffleActivityAccountDay.currentDay());
            raffleActivityAccountDay.setDayCount(createOrderAggregate.getDayCount());
            raffleActivityAccountDay.setDayCountSurplus(createOrderAggregate.getDayCount());

            dbRouter.doRouter(createOrderAggregate.getUserId());
            transactionTemplate.executeWithoutResult(status -> {
                try {
                    raffleActivityOrderDao.insert(raffleActivityOrder);
                    RaffleActivityAccount raffleActivityAccountRes = raffleActivityAccountDao.queryAccountByUserId(raffleActivityAccount);
                    if (null == raffleActivityAccountRes) {
                        raffleActivityAccountDao.insert(raffleActivityAccount);
                    } else {
                        raffleActivityAccountDao.updateAccountQuota(raffleActivityAccount);
                    }
                    raffleActivityAccountMonthDao.addAccountQuota(raffleActivityAccountMonth);
                    raffleActivityAccountDayDao.addAccountQuota(raffleActivityAccountDay);
                } catch (DuplicateKeyException e) {
                    status.setRollbackOnly();
                    log.error("写入订单记录，唯一索引冲突 userId: {} activityId: {} sku: {}",
                            activityOrderEntity.getUserId(), activityOrderEntity.getActivityId(), activityOrderEntity.getSku(), e);
                    throw new AppException(ResponseCode.INDEX_DUP.getCode(), e);
                }
            });
        } finally {
            dbRouter.clear();
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    public void doSaveCreditPayOrder(CreateQuotaOrderAggregate createOrderAggregate) {
        RLock lock = redisService.getLock(quotaOrderLockKey(createOrderAggregate));
        lock.lock();
        try {
            ActivityOrderEntity activityOrderEntity = createOrderAggregate.getActivityOrderEntity();
            RaffleActivityOrder raffleActivityOrder = buildRaffleActivityOrder(createOrderAggregate, activityOrderEntity);

            dbRouter.doRouter(createOrderAggregate.getUserId());
            transactionTemplate.executeWithoutResult(status -> {
                try {
                    raffleActivityOrderDao.insert(raffleActivityOrder);
                } catch (DuplicateKeyException e) {
                    status.setRollbackOnly();
                    log.error("写入订单记录，唯一索引冲突 userId: {} activityId: {} sku: {}",
                            activityOrderEntity.getUserId(), activityOrderEntity.getActivityId(), activityOrderEntity.getSku(), e);
                    throw new AppException(ResponseCode.INDEX_DUP.getCode(), e);
                }
            });
        } finally {
            dbRouter.clear();
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    public void updateOrder(DeliveryOrderEntity deliveryOrderEntity) {
        RLock lock = redisService.getLock(Constants.RedisKey.ACTIVITY_ACCOUNT_UPDATE_LOCK
                + deliveryOrderEntity.getUserId() + Constants.UNDERLINE + deliveryOrderEntity.getOutBusinessNo());
        lock.lock();
        try {
            dbRouter.doRouter(deliveryOrderEntity.getUserId());
            RaffleActivityOrder raffleActivityOrderReq = new RaffleActivityOrder();
            raffleActivityOrderReq.setUserId(deliveryOrderEntity.getUserId());
            raffleActivityOrderReq.setOutBusinessNo(deliveryOrderEntity.getOutBusinessNo());
            RaffleActivityOrder raffleActivityOrderRes = raffleActivityOrderDao.queryRaffleActivityOrder(raffleActivityOrderReq);

            if (null == raffleActivityOrderRes) {
                return;
            }

            RaffleActivityAccount raffleActivityAccount = new RaffleActivityAccount();
            raffleActivityAccount.setUserId(raffleActivityOrderRes.getUserId());
            raffleActivityAccount.setActivityId(raffleActivityOrderRes.getActivityId());
            raffleActivityAccount.setTotalCount(raffleActivityOrderRes.getTotalCount());
            raffleActivityAccount.setTotalCountSurplus(raffleActivityOrderRes.getTotalCount());
            raffleActivityAccount.setDayCount(raffleActivityOrderRes.getDayCount());
            raffleActivityAccount.setDayCountSurplus(raffleActivityOrderRes.getDayCount());
            raffleActivityAccount.setMonthCount(raffleActivityOrderRes.getMonthCount());
            raffleActivityAccount.setMonthCountSurplus(raffleActivityOrderRes.getMonthCount());

            RaffleActivityAccountMonth raffleActivityAccountMonth = new RaffleActivityAccountMonth();
            raffleActivityAccountMonth.setUserId(raffleActivityOrderRes.getUserId());
            raffleActivityAccountMonth.setActivityId(raffleActivityOrderRes.getActivityId());
            raffleActivityAccountMonth.setMonth(RaffleActivityAccountMonth.currentMonth());
            raffleActivityAccountMonth.setMonthCount(raffleActivityOrderRes.getMonthCount());
            raffleActivityAccountMonth.setMonthCountSurplus(raffleActivityOrderRes.getMonthCount());

            RaffleActivityAccountDay raffleActivityAccountDay = new RaffleActivityAccountDay();
            raffleActivityAccountDay.setUserId(raffleActivityOrderRes.getUserId());
            raffleActivityAccountDay.setActivityId(raffleActivityOrderRes.getActivityId());
            raffleActivityAccountDay.setDay(RaffleActivityAccountDay.currentDay());
            raffleActivityAccountDay.setDayCount(raffleActivityOrderRes.getDayCount());
            raffleActivityAccountDay.setDayCountSurplus(raffleActivityOrderRes.getDayCount());

            transactionTemplate.executeWithoutResult(status -> {
                try {
                    int updateCount = raffleActivityOrderDao.updateOrderCompleted(raffleActivityOrderReq);
                    if (1 != updateCount) {
                        status.setRollbackOnly();
                        return;
                    }
                    RaffleActivityAccount raffleActivityAccountRes = raffleActivityAccountDao.queryAccountByUserId(raffleActivityAccount);
                    if (null == raffleActivityAccountRes) {
                        raffleActivityAccountDao.insert(raffleActivityAccount);
                    } else {
                        raffleActivityAccountDao.updateAccountQuota(raffleActivityAccount);
                    }
                    raffleActivityAccountMonthDao.addAccountQuota(raffleActivityAccountMonth);
                    raffleActivityAccountDayDao.addAccountQuota(raffleActivityAccountDay);
                } catch (DuplicateKeyException e) {
                    status.setRollbackOnly();
                    log.error("更新订单记录，完成态，唯一索引冲突 userId: {} outBusinessNo: {}",
                            deliveryOrderEntity.getUserId(), deliveryOrderEntity.getOutBusinessNo(), e);
                    throw new AppException(ResponseCode.INDEX_DUP.getCode(), e);
                }
            });
        } finally {
            dbRouter.clear();
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private static RaffleActivityOrder buildRaffleActivityOrder(
            CreateQuotaOrderAggregate createOrderAggregate, ActivityOrderEntity activityOrderEntity) {
        RaffleActivityOrder raffleActivityOrder = new RaffleActivityOrder();
        raffleActivityOrder.setUserId(activityOrderEntity.getUserId());
        raffleActivityOrder.setSku(activityOrderEntity.getSku());
        raffleActivityOrder.setActivityId(activityOrderEntity.getActivityId());
        raffleActivityOrder.setActivityName(activityOrderEntity.getActivityName());
        raffleActivityOrder.setStrategyId(activityOrderEntity.getStrategyId());
        raffleActivityOrder.setOrderId(activityOrderEntity.getOrderId());
        raffleActivityOrder.setOrderTime(activityOrderEntity.getOrderTime());
        raffleActivityOrder.setTotalCount(createOrderAggregate.getTotalCount());
        raffleActivityOrder.setDayCount(createOrderAggregate.getDayCount());
        raffleActivityOrder.setMonthCount(createOrderAggregate.getMonthCount());
        raffleActivityOrder.setPayAmount(activityOrderEntity.getPayAmount());
        raffleActivityOrder.setState(activityOrderEntity.getState().getCode());
        raffleActivityOrder.setOutBusinessNo(activityOrderEntity.getOutBusinessNo());
        return raffleActivityOrder;
    }
}

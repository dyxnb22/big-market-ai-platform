package com.dyx.market.infrastructure.adapter.repository;

import com.dyx.market.domain.activity.model.aggregate.CreatePartakeOrderAggregate;
import com.dyx.market.domain.activity.model.entity.ActivityAccountDayEntity;
import com.dyx.market.domain.activity.model.entity.ActivityAccountMonthEntity;
import com.dyx.market.domain.activity.model.entity.UserRaffleOrderEntity;
import com.dyx.market.infrastructure.dao.*;
import com.dyx.market.infrastructure.dao.po.*;
import com.dyx.market.middleware.db.router.strategy.IDBRouterStrategy;
import com.dyx.market.types.enums.ResponseCode;
import com.dyx.market.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.Resource;

/**
 * 参与抽奖订单持久化：本地事务内扣减总/月/日额度并写入 {@code user_raffle_order}。
 */
@Slf4j
@Component
public class ActivityPartakeOrderSupport {

    @Resource
    private IRaffleActivityAccountDao raffleActivityAccountDao;
    @Resource
    private IRaffleActivityAccountMonthDao raffleActivityAccountMonthDao;
    @Resource
    private IRaffleActivityAccountDayDao raffleActivityAccountDayDao;
    @Resource
    private IUserRaffleOrderDao userRaffleOrderDao;
    @Resource
    private TransactionTemplate transactionTemplate;
    @Resource
    private IDBRouterStrategy dbRouter;

    public void saveCreatePartakeOrderAggregate(CreatePartakeOrderAggregate createPartakeOrderAggregate) {
        try {
            String userId = createPartakeOrderAggregate.getUserId();
            Long activityId = createPartakeOrderAggregate.getActivityId();
            ActivityAccountMonthEntity activityAccountMonthEntity = createPartakeOrderAggregate.getActivityAccountMonthEntity();
            ActivityAccountDayEntity activityAccountDayEntity = createPartakeOrderAggregate.getActivityAccountDayEntity();
            UserRaffleOrderEntity userRaffleOrderEntity = createPartakeOrderAggregate.getUserRaffleOrderEntity();

            dbRouter.doRouter(userId);
            transactionTemplate.executeWithoutResult(status -> {
                    try {
                        int totalCount = raffleActivityAccountDao.updateActivityAccountSubtractionQuota(
                                RaffleActivityAccount.builder()
                                        .userId(userId)
                                        .activityId(activityId)
                                        .build());
                        if (1 != totalCount) {
                            status.setRollbackOnly();
                            log.warn("写入创建参与活动记录，更新总账户额度不足，异常 userId: {} activityId: {}", userId, activityId);
                            throw new AppException(ResponseCode.ACCOUNT_QUOTA_ERROR.getCode(), ResponseCode.ACCOUNT_QUOTA_ERROR.getInfo());
                        }

                        applyMonthQuota(createPartakeOrderAggregate, userId, activityId, activityAccountMonthEntity, status);
                        applyDayQuota(createPartakeOrderAggregate, userId, activityId, activityAccountDayEntity, status);

                        userRaffleOrderDao.insert(UserRaffleOrder.builder()
                                .userId(userRaffleOrderEntity.getUserId())
                                .activityId(userRaffleOrderEntity.getActivityId())
                                .activityName(userRaffleOrderEntity.getActivityName())
                                .strategyId(userRaffleOrderEntity.getStrategyId())
                                .orderId(userRaffleOrderEntity.getOrderId())
                                .orderTime(userRaffleOrderEntity.getOrderTime())
                                .orderState(userRaffleOrderEntity.getOrderState().getCode())
                                .build());
                    } catch (DuplicateKeyException e) {
                        status.setRollbackOnly();
                        log.error("写入创建参与活动记录，唯一索引冲突 userId: {} activityId: {}", userId, activityId, e);
                        throw new AppException(ResponseCode.INDEX_DUP.getCode(), e);
                    }
            });
        } finally {
            dbRouter.clear();
        }
    }

    private void applyMonthQuota(CreatePartakeOrderAggregate aggregate, String userId, Long activityId,
                                 ActivityAccountMonthEntity monthEntity,
                                 org.springframework.transaction.TransactionStatus status) {
        if (aggregate.isExistAccountMonth()) {
            int updateMonthCount = raffleActivityAccountMonthDao.updateActivityAccountMonthSubtractionQuota(
                    RaffleActivityAccountMonth.builder()
                            .userId(userId)
                            .activityId(activityId)
                            .month(monthEntity.getMonth())
                            .build());
            if (1 != updateMonthCount) {
                status.setRollbackOnly();
                log.warn("写入创建参与活动记录，更新月账户额度不足，异常 userId: {} activityId: {} month: {}",
                        userId, activityId, monthEntity.getMonth());
                throw new AppException(ResponseCode.ACCOUNT_MONTH_QUOTA_ERROR.getCode(),
                        ResponseCode.ACCOUNT_MONTH_QUOTA_ERROR.getInfo());
            }
            raffleActivityAccountDao.updateActivityAccountMonthSubtractionQuota(
                    RaffleActivityAccount.builder().userId(userId).activityId(activityId).build());
            return;
        }
        raffleActivityAccountMonthDao.insertActivityAccountMonth(RaffleActivityAccountMonth.builder()
                .userId(monthEntity.getUserId())
                .activityId(monthEntity.getActivityId())
                .month(monthEntity.getMonth())
                .monthCount(monthEntity.getMonthCount())
                .monthCountSurplus(monthEntity.getMonthCountSurplus() - 1)
                .build());
        raffleActivityAccountDao.updateActivityAccountMonthSurplusImageQuota(RaffleActivityAccount.builder()
                .userId(userId)
                .activityId(activityId)
                .monthCountSurplus(monthEntity.getMonthCountSurplus())
                .build());
    }

    private void applyDayQuota(CreatePartakeOrderAggregate aggregate, String userId, Long activityId,
                               ActivityAccountDayEntity dayEntity,
                               org.springframework.transaction.TransactionStatus status) {
        if (aggregate.isExistAccountDay()) {
            int updateDayCount = raffleActivityAccountDayDao.updateActivityAccountDaySubtractionQuota(
                    RaffleActivityAccountDay.builder()
                            .userId(userId)
                            .activityId(activityId)
                            .day(dayEntity.getDay())
                            .build());
            if (1 != updateDayCount) {
                status.setRollbackOnly();
                log.warn("写入创建参与活动记录，更新日账户额度不足，异常 userId: {} activityId: {} day: {}",
                        userId, activityId, dayEntity.getDay());
                throw new AppException(ResponseCode.ACCOUNT_DAY_QUOTA_ERROR.getCode(),
                        ResponseCode.ACCOUNT_DAY_QUOTA_ERROR.getInfo());
            }
            raffleActivityAccountDao.updateActivityAccountDaySubtractionQuota(
                    RaffleActivityAccount.builder().userId(userId).activityId(activityId).build());
            return;
        }
        raffleActivityAccountDayDao.insertActivityAccountDay(RaffleActivityAccountDay.builder()
                .userId(dayEntity.getUserId())
                .activityId(dayEntity.getActivityId())
                .day(dayEntity.getDay())
                .dayCount(dayEntity.getDayCount())
                .dayCountSurplus(dayEntity.getDayCountSurplus() - 1)
                .build());
        raffleActivityAccountDao.updateActivityAccountDaySurplusImageQuota(RaffleActivityAccount.builder()
                .userId(userId)
                .activityId(activityId)
                .dayCountSurplus(dayEntity.getDayCountSurplus())
                .build());
    }
}

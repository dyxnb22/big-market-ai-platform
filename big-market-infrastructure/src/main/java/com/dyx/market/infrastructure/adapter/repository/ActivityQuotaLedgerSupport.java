package com.dyx.market.infrastructure.adapter.repository;

import com.dyx.market.domain.activity.model.aggregate.CreatePartakeOrderAggregate;
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
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;

/**
 * 活动额度账本、扣减回滚与参与订单补偿，从 {@link ActivityRepository} 拆分。
 */
@Slf4j
@Component
public class ActivityQuotaLedgerSupport {

    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Resource
    private IRaffleActivityAccountDao raffleActivityAccountDao;
    @Resource
    private IRaffleActivityAccountMonthDao raffleActivityAccountMonthDao;
    @Resource
    private IRaffleActivityAccountDayDao raffleActivityAccountDayDao;
    @Resource
    private IUserRaffleOrderDao userRaffleOrderDao;
    @Resource
    private IRaffleQuotaDecrementLedgerDao raffleQuotaDecrementLedgerDao;
    @Resource
    private TransactionTemplate transactionTemplate;
    @Resource
    private IDBRouterStrategy dbRouter;

    public boolean decrementQuotaWithLedger(String userId, Long activityId, String outBusinessNo) {
        String month = RaffleActivityAccountMonth.currentMonth();
        String day = RaffleActivityAccountDay.currentDay();
        try {
            dbRouter.doRouter(userId);
            Boolean result = transactionTemplate.execute(status -> {
                try {
                    raffleQuotaDecrementLedgerDao.insert(
                            RaffleQuotaDecrementLedger.builder()
                                    .userId(userId)
                                    .activityId(activityId)
                                    .outBusinessNo(outBusinessNo)
                                    .month(month)
                                    .day(day)
                                    .build());
                } catch (DuplicateKeyException e) {
                    log.info("[decrementQuotaWithLedger] duplicate userId:{} activityId:{} outBusinessNo:{}",
                            userId, activityId, outBusinessNo);
                    return true;
                }

                int totalUpdated = raffleActivityAccountDao.updateActivityAccountSubtractionQuota(
                        RaffleActivityAccount.builder().userId(userId).activityId(activityId).build());
                if (totalUpdated != 1) {
                    status.setRollbackOnly();
                    log.warn("[decrementQuotaWithLedger] total quota exhausted userId:{} activityId:{}", userId, activityId);
                    return false;
                }

                RaffleActivityAccount totalAccount = raffleActivityAccountDao.queryActivityAccountByUserId(
                        RaffleActivityAccount.builder().userId(userId).activityId(activityId).build());

                return decrementMonthQuota(userId, activityId, month, totalAccount, status)
                        && decrementDayQuota(userId, activityId, day, totalAccount, status);
            });
            return Boolean.TRUE.equals(result);
        } finally {
            dbRouter.clear();
        }
    }

    private boolean decrementMonthQuota(String userId, Long activityId, String month,
                                        RaffleActivityAccount totalAccount,
                                        org.springframework.transaction.TransactionStatus status) {
        RaffleActivityAccountMonth monthAccount = raffleActivityAccountMonthDao.queryActivityAccountMonthByUserId(
                RaffleActivityAccountMonth.builder().userId(userId).activityId(activityId).month(month).build());
        if (monthAccount != null) {
            int monthUpdated = raffleActivityAccountMonthDao.updateActivityAccountMonthSubtractionQuota(
                    RaffleActivityAccountMonth.builder().userId(userId).activityId(activityId).month(month).build());
            if (monthUpdated != 1) {
                status.setRollbackOnly();
                return false;
            }
            raffleActivityAccountDao.updateActivityAccountMonthSubtractionQuota(
                    RaffleActivityAccount.builder().userId(userId).activityId(activityId).build());
            return true;
        }
        raffleActivityAccountMonthDao.insertActivityAccountMonth(
                RaffleActivityAccountMonth.builder()
                        .userId(userId).activityId(activityId).month(month)
                        .monthCount(totalAccount.getMonthCount())
                        .monthCountSurplus(totalAccount.getMonthCount() - 1)
                        .build());
        raffleActivityAccountDao.updateActivityAccountMonthSurplusImageQuota(
                RaffleActivityAccount.builder()
                        .userId(userId).activityId(activityId)
                        .monthCountSurplus(totalAccount.getMonthCount())
                        .build());
        return true;
    }

    private boolean decrementDayQuota(String userId, Long activityId, String day,
                                      RaffleActivityAccount totalAccount,
                                      org.springframework.transaction.TransactionStatus status) {
        RaffleActivityAccountDay dayAccount = raffleActivityAccountDayDao.queryActivityAccountDayByUserId(
                RaffleActivityAccountDay.builder().userId(userId).activityId(activityId).day(day).build());
        if (dayAccount != null) {
            int dayUpdated = raffleActivityAccountDayDao.updateActivityAccountDaySubtractionQuota(
                    RaffleActivityAccountDay.builder().userId(userId).activityId(activityId).day(day).build());
            if (dayUpdated != 1) {
                status.setRollbackOnly();
                return false;
            }
            raffleActivityAccountDao.updateActivityAccountDaySubtractionQuota(
                    RaffleActivityAccount.builder().userId(userId).activityId(activityId).build());
            return true;
        }
        raffleActivityAccountDayDao.insertActivityAccountDay(
                RaffleActivityAccountDay.builder()
                        .userId(userId).activityId(activityId).day(day)
                        .dayCount(totalAccount.getDayCount())
                        .dayCountSurplus(totalAccount.getDayCount() - 1)
                        .build());
        raffleActivityAccountDao.updateActivityAccountDaySurplusImageQuota(
                RaffleActivityAccount.builder()
                        .userId(userId).activityId(activityId)
                        .dayCountSurplus(totalAccount.getDayCount())
                        .build());
        return true;
    }

    public boolean rollbackQuotaWithLedger(String userId, Long activityId, String outBusinessNo) {
        try {
            dbRouter.doRouter(userId);
            Boolean result = transactionTemplate.execute(status -> {
                RaffleQuotaDecrementLedger ledger = raffleQuotaDecrementLedgerDao.queryByKey(
                        RaffleQuotaDecrementLedger.builder()
                                .userId(userId).activityId(activityId).outBusinessNo(outBusinessNo).build());
                if (ledger == null || "rolled_back".equals(ledger.getStatus())) {
                    return true;
                }
                int updated = raffleQuotaDecrementLedgerDao.updateStatusToRolledBack(
                        RaffleQuotaDecrementLedger.builder()
                                .userId(userId).activityId(activityId).outBusinessNo(outBusinessNo).build());
                if (updated != 1) {
                    return true;
                }
                raffleActivityAccountDao.addAccountTotalSurplusQuota(
                        RaffleActivityAccount.builder().userId(userId).activityId(activityId).build());
                restoreMonthDayQuota(userId, activityId, ledger.getMonth(), ledger.getDay());
                return true;
            });
            return Boolean.TRUE.equals(result);
        } finally {
            dbRouter.clear();
        }
    }

    public boolean markRaffleOrderFailed(String userId, String orderId) {
        try {
            dbRouter.doRouter(userId);
            return userRaffleOrderDao.updateUserRaffleOrderStateFailed(UserRaffleOrder.builder()
                    .userId(userId).orderId(orderId).build()) == 1;
        } finally {
            dbRouter.clear();
        }
    }

    public void compensatePartakeQuota(String userId, Long activityId, String orderId, Date orderTime) {
        LocalDate date = orderTime.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        String month = date.format(MONTH_FMT);
        String day = date.format(DAY_FMT);
        try {
            dbRouter.doRouter(userId);
            Integer restored = transactionTemplate.execute(status -> {
                if (userRaffleOrderDao.updateUserRaffleOrderStateFailed(UserRaffleOrder.builder()
                        .userId(userId).orderId(orderId).build()) != 1) {
                    return 0;
                }
                RaffleActivityAccount account = RaffleActivityAccount.builder().userId(userId).activityId(activityId).build();
                raffleActivityAccountDao.addAccountTotalSurplusQuota(account);
                restoreMonthDayQuota(userId, activityId, month, day);
                return 1;
            });
            if (restored != null && restored == 1) {
                log.info("[compensate] draw quota restored userId:{} activityId:{} orderId:{}", userId, activityId, orderId);
            }
        } finally {
            dbRouter.clear();
        }
    }

    public void savePartakeOrderOnly(CreatePartakeOrderAggregate aggregate) {
        try {
            String userId = aggregate.getUserId();
            UserRaffleOrderEntity order = aggregate.getUserRaffleOrderEntity();
            dbRouter.doRouter(userId);
            transactionTemplate.execute(status -> {
                try {
                    userRaffleOrderDao.insert(UserRaffleOrder.builder()
                            .userId(order.getUserId())
                            .activityId(order.getActivityId())
                            .activityName(order.getActivityName())
                            .strategyId(order.getStrategyId())
                            .orderId(order.getOrderId())
                            .orderTime(order.getOrderTime())
                            .orderState(order.getOrderState().getCode())
                            .build());
                    return 1;
                } catch (DuplicateKeyException e) {
                    status.setRollbackOnly();
                    throw new AppException(ResponseCode.INDEX_DUP.getCode(), e);
                }
            });
        } finally {
            dbRouter.clear();
        }
    }

    private void restoreMonthDayQuota(String userId, Long activityId, String month, String day) {
        RaffleActivityAccount account = RaffleActivityAccount.builder().userId(userId).activityId(activityId).build();
        RaffleActivityAccountMonth monthAccount = raffleActivityAccountMonthDao.queryActivityAccountMonthByUserId(
                RaffleActivityAccountMonth.builder().userId(userId).activityId(activityId).month(month).build());
        if (monthAccount != null) {
            raffleActivityAccountMonthDao.addAccountQuota(
                    RaffleActivityAccountMonth.builder()
                            .userId(userId).activityId(activityId).month(month)
                            .monthCountSurplus(1).monthCount(0).build());
            raffleActivityAccountDao.addAccountMonthSurplusQuota(account);
        }
        RaffleActivityAccountDay dayAccount = raffleActivityAccountDayDao.queryActivityAccountDayByUserId(
                RaffleActivityAccountDay.builder().userId(userId).activityId(activityId).day(day).build());
        if (dayAccount != null) {
            raffleActivityAccountDayDao.addAccountQuota(
                    RaffleActivityAccountDay.builder()
                            .userId(userId).activityId(activityId).day(day)
                            .dayCountSurplus(1).dayCount(0).build());
            raffleActivityAccountDao.addAccountDaySurplusQuota(account);
        }
    }
}

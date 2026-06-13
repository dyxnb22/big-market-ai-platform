package com.dyx.market.domain.activity.service.partake;

import com.dyx.market.domain.activity.adapter.port.IActivityAccountPort;
import com.dyx.market.domain.activity.model.aggregate.CreatePartakeOrderAggregate;
import com.dyx.market.domain.activity.model.entity.*;
import com.dyx.market.domain.activity.model.valobj.UserRaffleOrderStateVO;
import com.dyx.market.domain.activity.adapter.repository.IActivityRepository;
import com.dyx.market.types.enums.ResponseCode;
import com.dyx.market.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import com.dyx.market.types.common.OrderIdGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;

/**
 * @author Fuzhengwei bugstack.cn @小傅哥
 * @description
 * @create 2024-04-05 07:53
 */
@Slf4j
@Service
public class RaffleActivityPartakeService extends AbstractRaffleActivityPartake {

    private static final DateTimeFormatter DATE_FORMAT_MONTH = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final DateTimeFormatter DATE_FORMAT_DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final IActivityAccountPort activityAccountPort;
    private final boolean remoteQuotaDecrementEnabled;

    public RaffleActivityPartakeService(IActivityRepository activityRepository,
                                        IActivityAccountPort activityAccountPort,
                                        @Value("${account.service.remote-quota-decrement.enabled:false}") boolean remoteQuotaDecrementEnabled) {
        super(activityRepository);
        this.activityAccountPort = activityAccountPort;
        this.remoteQuotaDecrementEnabled = remoteQuotaDecrementEnabled;
    }

    @Override
    protected CreatePartakeOrderAggregate doFilterAccount(String userId, Long activityId, Date currentDate) {
        // These three reads are a pre-flight check outside the write transaction.
        // They are NOT the atomicity boundary — the real guard is the DB-level
        // WHERE surplus > 0 inside saveCreatePartakeOrderAggregate. Concurrent
        // requests can both pass here and one will fail at the DB update; the
        // trade-off is an early, user-friendly error for the common case.

        // 查询总账户额度
        ActivityAccountEntity activityAccountEntity = activityRepository.queryActivityAccountByUserId(userId, activityId);

        // 额度判断（只判断总剩余额度）
        if (null == activityAccountEntity || activityAccountEntity.getTotalCountSurplus() <= 0) {
            throw new AppException(ResponseCode.ACCOUNT_QUOTA_ERROR.getCode(), ResponseCode.ACCOUNT_QUOTA_ERROR.getInfo());
        }

        LocalDate localDate = currentDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        String month = localDate.format(DATE_FORMAT_MONTH);
        String day = localDate.format(DATE_FORMAT_DAY);

        // 查询月账户额度
        ActivityAccountMonthEntity activityAccountMonthEntity = activityRepository.queryActivityAccountMonthByUserId(userId, activityId, month);
        if (null != activityAccountMonthEntity && activityAccountMonthEntity.getMonthCountSurplus() <= 0) {
            throw new AppException(ResponseCode.ACCOUNT_MONTH_QUOTA_ERROR.getCode(), ResponseCode.ACCOUNT_MONTH_QUOTA_ERROR.getInfo());
        }

        // 创建月账户额度；true = 存在月账户、false = 不存在月账户
        boolean isExistAccountMonth = null != activityAccountMonthEntity;
        if (null == activityAccountMonthEntity) {
            activityAccountMonthEntity = new ActivityAccountMonthEntity();
            activityAccountMonthEntity.setUserId(userId);
            activityAccountMonthEntity.setActivityId(activityId);
            activityAccountMonthEntity.setMonth(month);
            activityAccountMonthEntity.setMonthCount(activityAccountEntity.getMonthCount());
            activityAccountMonthEntity.setMonthCountSurplus(activityAccountEntity.getMonthCount());
        }

        // 查询日账户额度
        ActivityAccountDayEntity activityAccountDayEntity = activityRepository.queryActivityAccountDayByUserId(userId, activityId, day);
        if (null != activityAccountDayEntity && activityAccountDayEntity.getDayCountSurplus() <= 0) {
            throw new AppException(ResponseCode.ACCOUNT_DAY_QUOTA_ERROR.getCode(), ResponseCode.ACCOUNT_DAY_QUOTA_ERROR.getInfo());
        }

        // 创建日账户额度；true = 存在日账户、false = 不存在日账户
        boolean isExistAccountDay = null != activityAccountDayEntity;
        if (null == activityAccountDayEntity) {
            activityAccountDayEntity = new ActivityAccountDayEntity();
            activityAccountDayEntity.setUserId(userId);
            activityAccountDayEntity.setActivityId(activityId);
            activityAccountDayEntity.setDay(day);
            activityAccountDayEntity.setDayCount(activityAccountEntity.getDayCount());
            activityAccountDayEntity.setDayCountSurplus(activityAccountEntity.getDayCount());
        }

        // 构建对象
        CreatePartakeOrderAggregate createPartakeOrderAggregate = new CreatePartakeOrderAggregate();
        createPartakeOrderAggregate.setUserId(userId);
        createPartakeOrderAggregate.setActivityId(activityId);
        createPartakeOrderAggregate.setActivityAccountEntity(activityAccountEntity);
        createPartakeOrderAggregate.setExistAccountMonth(isExistAccountMonth);
        createPartakeOrderAggregate.setActivityAccountMonthEntity(activityAccountMonthEntity);
        createPartakeOrderAggregate.setExistAccountDay(isExistAccountDay);
        createPartakeOrderAggregate.setActivityAccountDayEntity(activityAccountDayEntity);

        return createPartakeOrderAggregate;
    }

    @Override
    protected UserRaffleOrderEntity buildUserRaffleOrder(String userId, Long activityId, Date currentDate) {
        ActivityEntity activityEntity = activityRepository.queryRaffleActivityByActivityId(activityId);
        // 构建订单
        UserRaffleOrderEntity userRaffleOrder = new UserRaffleOrderEntity();
        userRaffleOrder.setUserId(userId);
        userRaffleOrder.setActivityId(activityId);
        userRaffleOrder.setActivityName(activityEntity.getActivityName());
        userRaffleOrder.setStrategyId(activityEntity.getStrategyId());
        userRaffleOrder.setOrderId(OrderIdGenerator.generate(12));
        userRaffleOrder.setOrderTime(currentDate);
        userRaffleOrder.setOrderState(UserRaffleOrderStateVO.create);
        userRaffleOrder.setEndDateTime(activityEntity.getEndDateTime());
        return userRaffleOrder;
    }

    /**
     * Phase 2.2-B14 flag-gated wiring.
     *
     * flag=false (default): original saveCreatePartakeOrderAggregate — quota decrement and
     *   order insert happen atomically in a single local transaction. No change to runtime behavior.
     *
     * flag=true: pre-draw remote quota decrement via IActivityAccountPort.decrementQuota,
     *   then savePartakeOrderOnly to insert only the order row. If the order insert fails,
     *   rollbackQuota restores the slot (saga compensation).
     */
    @Override
    protected void doSavePartakeOrder(CreatePartakeOrderAggregate aggregate) {
        if (!remoteQuotaDecrementEnabled) {
            activityRepository.saveCreatePartakeOrderAggregate(aggregate);
            return;
        }
        String userId = aggregate.getUserId();
        Long activityId = aggregate.getActivityId();
        String outBusinessNo = aggregate.getUserRaffleOrderEntity().getOrderId();
        boolean decremented = activityAccountPort.decrementQuota(userId, activityId, outBusinessNo);
        if (!decremented) {
            log.warn("[RaffleActivityPartakeService] remote decrementQuota exhausted userId:{} activityId:{}", userId, activityId);
            throw new AppException(ResponseCode.ACCOUNT_QUOTA_ERROR.getCode(), ResponseCode.ACCOUNT_QUOTA_ERROR.getInfo());
        }
        try {
            activityRepository.savePartakeOrderOnly(aggregate);
        } catch (Exception e) {
            log.error("[RaffleActivityPartakeService] savePartakeOrderOnly failed, compensating rollbackQuota userId:{} activityId:{} outBusinessNo:{}",
                    userId, activityId, outBusinessNo, e);
            activityAccountPort.rollbackQuota(userId, activityId, outBusinessNo);
            throw e;
        }
    }

}

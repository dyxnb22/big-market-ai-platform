package com.dyx.market.domain.activity.application;

import com.dyx.market.domain.activity.adapter.port.IAwardFulfillmentPort;
import com.dyx.market.domain.activity.adapter.port.IActivityAccountPort;
import com.dyx.market.domain.activity.adapter.port.IStrategyDecisionPort;
import com.dyx.market.domain.activity.adapter.repository.IActivityRepository;
import com.dyx.market.domain.activity.model.entity.PartakeRaffleActivityEntity;
import com.dyx.market.domain.activity.model.entity.UserRaffleOrderEntity;
import com.dyx.market.domain.activity.service.IRaffleActivityPartakeService;
import com.dyx.market.domain.award.model.entity.UserAwardRecordEntity;
import com.dyx.market.domain.award.model.valobj.AwardStateVO;
import com.dyx.market.domain.strategy.model.entity.RaffleAwardEntity;
import com.dyx.market.domain.strategy.model.entity.RaffleFactorEntity;
import com.dyx.market.types.enums.ResponseCode;
import com.dyx.market.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Date;

@Slf4j
@Service
public class RaffleApplicationService {

    @Resource
    private IRaffleActivityPartakeService raffleActivityPartakeService;
    @Resource
    private IStrategyDecisionPort strategyDecisionPort;
    @Resource
    private IAwardFulfillmentPort awardFulfillmentPort;
    @Resource
    private IActivityRepository activityRepository;
    @Resource
    private IActivityAccountPort activityAccountPort;
    @Value("${account.service.remote-quota-decrement.enabled:false}")
    private boolean remoteQuotaDecrementEnabled;

    public ActivityDrawResponseEntity executeDraw(ActivityDrawRequestEntity request) {
        String userId = request.getUserId();
        Long activityId = request.getActivityId();

        log.info("活动抽奖开始 userId:{} activityId:{}", userId, activityId);

        // 1. 参数校验
        if (StringUtils.isBlank(userId) || null == activityId) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), ResponseCode.ILLEGAL_PARAMETER.getInfo());
        }

        UserRaffleOrderEntity existingOrder = activityRepository.queryNoUsedRaffleOrder(PartakeRaffleActivityEntity.builder()
                .userId(userId)
                .activityId(activityId)
                .build());

        // 2. 参与活动 - 创建参与记录订单（含额度扣减）
        UserRaffleOrderEntity orderEntity = raffleActivityPartakeService.createOrder(userId, activityId);
        boolean quotaDecrementedInThisCall = existingOrder == null;
        log.info("活动抽奖，创建订单 userId:{} activityId:{} orderId:{}", userId, activityId, orderEntity.getOrderId());

        try {
            // 3. 抽奖策略 - 执行抽奖
            RaffleAwardEntity raffleAwardEntity = strategyDecisionPort.performRaffle(RaffleFactorEntity.builder()
                    .userId(orderEntity.getUserId())
                    .strategyId(orderEntity.getStrategyId())
                    .endDateTime(orderEntity.getEndDateTime())
                    .build());

            // 4. 存放结果 - 写入中奖记录
            UserAwardRecordEntity userAwardRecord = UserAwardRecordEntity.builder()
                    .userId(orderEntity.getUserId())
                    .activityId(orderEntity.getActivityId())
                    .strategyId(orderEntity.getStrategyId())
                    .orderId(orderEntity.getOrderId())
                    .awardId(raffleAwardEntity.getAwardId())
                    .awardTitle(raffleAwardEntity.getAwardTitle())
                    .awardTime(new Date())
                    .awardState(AwardStateVO.create)
                    .awardConfig(raffleAwardEntity.getAwardConfig())
                    .build();

            awardFulfillmentPort.saveUserAwardRecord(userAwardRecord);

            return ActivityDrawResponseEntity.builder()
                    .awardId(raffleAwardEntity.getAwardId())
                    .awardTitle(raffleAwardEntity.getAwardTitle())
                    .awardIndex(raffleAwardEntity.getSort())
                    .build();
        } catch (Exception e) {
            // Quota was already decremented in step 2 but raffle/award failed.
            // Compensate by restoring the quota slot so the user doesn't lose their draw chance.
            log.error("活动抽奖执行异常，补偿回退额度 userId:{} activityId:{} orderId:{}", userId, activityId, orderEntity.getOrderId(), e);
            if (quotaDecrementedInThisCall) {
                try {
                    if (remoteQuotaDecrementEnabled) {
                        if (activityRepository.markRaffleOrderFailed(userId, orderEntity.getOrderId())) {
                            activityAccountPort.rollbackQuota(userId, activityId, orderEntity.getOrderId());
                        } else {
                            log.warn("活动抽奖订单已非创建态，跳过远程额度回滚避免重复补偿 userId:{} activityId:{} orderId:{}",
                                    userId, activityId, orderEntity.getOrderId());
                        }
                    } else {
                        activityRepository.compensatePartakeQuota(userId, activityId, orderEntity.getOrderId());
                    }
                    log.info("活动抽奖补偿回退额度完成 userId:{} activityId:{} orderId:{}", userId, activityId, orderEntity.getOrderId());
                } catch (Exception ce) {
                    log.error("活动抽奖补偿回退额度失败 userId:{} activityId:{} orderId:{}", userId, activityId, orderEntity.getOrderId(), ce);
                }
            } else {
                log.warn("活动抽奖复用未完成订单失败，跳过额度补偿避免重复返还 userId:{} activityId:{} orderId:{}",
                        userId, activityId, orderEntity.getOrderId());
            }
            throw e;
        }
    }

}

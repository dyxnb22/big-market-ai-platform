package com.dyx.market.domain.activity.application;

import com.dyx.market.domain.activity.adapter.port.IAwardFulfillmentPort;
import com.dyx.market.domain.activity.adapter.port.IActivityAccountPort;
import com.dyx.market.domain.activity.adapter.port.IStrategyDecisionPort;
import com.dyx.market.domain.activity.model.entity.UserRaffleOrderEntity;
import com.dyx.market.domain.activity.service.IRaffleActivityPartakeService;
import com.dyx.market.domain.award.model.entity.UserAwardRecordEntity;
import com.dyx.market.domain.award.model.valobj.AwardStateVO;
import com.dyx.market.domain.strategy.model.entity.RaffleAwardEntity;
import com.dyx.market.domain.strategy.model.entity.RaffleFactorEntity;
import com.dyx.market.domain.strategy.adapter.port.IStrategyStockConfirmCompensationPort;
import com.dyx.market.domain.strategy.repository.IStrategyRepository;
import com.dyx.market.types.enums.ResponseCode;
import com.dyx.market.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.Date;

/**
 * 活动抽奖领域应用服务：编排参与下单、策略决策、发奖记录与失败额度补偿。
 *
 * <p>位于 domain 层，由 trigger 模块 {@code RaffleDrawApplicationService} 等调用方驱动。</p>
 */
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
    private IActivityAccountPort activityAccountPort;
    @Resource
    private IStrategyRepository strategyRepository;
    @Resource
    private IStrategyStockConfirmCompensationPort strategyStockConfirmCompensationPort;
    /**
     * 执行一次活动抽奖：创建参与单 → 策略出奖 → 落中奖记录；异常时补偿回退额度。
     *
     * @param request 用户 ID、活动 ID
     * @return 中奖奖品 ID、标题与序号
     */
    public ActivityDrawResponseEntity executeDraw(ActivityDrawRequestEntity request) {
        String userId = request.getUserId();
        Long activityId = request.getActivityId();

        log.info("活动抽奖开始 userId:{} activityId:{}", userId, activityId);

        // 1. 参数校验
        if (StringUtils.isBlank(userId) || null == activityId) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), ResponseCode.ILLEGAL_PARAMETER.getInfo());
        }

        // 2. 参与活动 - 创建参与记录订单（含额度扣减；createOrder 内部处理订单复用）
        UserRaffleOrderEntity orderEntity = raffleActivityPartakeService.createOrder(userId, activityId);
        log.info("活动抽奖，创建订单 userId:{} activityId:{} orderId:{}", userId, activityId, orderEntity.getOrderId());

        RaffleAwardEntity raffleAwardEntity = null;
        boolean awardSaved = false;
        try {
            // 3. 抽奖策略 - 执行抽奖
            raffleAwardEntity = strategyDecisionPort.performRaffle(RaffleFactorEntity.builder()
                    .userId(orderEntity.getUserId())
                    .strategyId(orderEntity.getStrategyId())
                    .endDateTime(orderEntity.getEndDateTime())
                    .orderId(orderEntity.getOrderId())
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
            awardSaved = true;

            if (Boolean.TRUE.equals(raffleAwardEntity.getStockReserved()) && null != raffleAwardEntity.getStockReservation()) {
                try {
                    strategyRepository.confirmAwardStockReservation(raffleAwardEntity.getStockReservation());
                } catch (Exception confirmEx) {
                    log.error("活动抽奖奖品库存确认失败，写入补偿任务 userId:{} activityId:{} orderId:{}",
                            userId, activityId, orderEntity.getOrderId(), confirmEx);
                    strategyStockConfirmCompensationPort.enqueuePendingConfirm(
                            userId, raffleAwardEntity.getStockReservation());
                }
            }

            return ActivityDrawResponseEntity.builder()
                    .awardId(raffleAwardEntity.getAwardId())
                    .awardTitle(raffleAwardEntity.getAwardTitle())
                    .awardIndex(raffleAwardEntity.getSort())
                    .build();
        } catch (Exception e) {
            if (!awardSaved
                    && null != raffleAwardEntity
                    && Boolean.TRUE.equals(raffleAwardEntity.getStockReserved())
                    && null != raffleAwardEntity.getStockReservation()) {
                try {
                    strategyRepository.releaseAwardStockReservation(raffleAwardEntity.getStockReservation());
                    log.info("活动抽奖异常，释放奖品库存预占 userId:{} activityId:{} orderId:{}",
                            userId, activityId, orderEntity.getOrderId());
                } catch (Exception releaseEx) {
                    log.error("活动抽奖释放奖品库存预占失败 userId:{} activityId:{} orderId:{}",
                            userId, activityId, orderEntity.getOrderId(), releaseEx);
                }
            }

            // 只有中奖记录尚未落库时才补偿参与额度；记录已落库则保留中奖事实，避免重复返还。
            if (!awardSaved) {
                log.error("活动抽奖执行异常，补偿回退额度 userId:{} activityId:{} orderId:{}", userId, activityId, orderEntity.getOrderId(), e);
                try {
                    activityAccountPort.compensatePartakeOrder(
                            userId, activityId, orderEntity.getOrderId(), orderEntity.getOrderTime());
                    log.info("活动抽奖补偿回退额度完成 userId:{} activityId:{} orderId:{}", userId, activityId, orderEntity.getOrderId());
                } catch (Exception ce) {
                    log.error("活动抽奖补偿回退额度失败 userId:{} activityId:{} orderId:{}", userId, activityId, orderEntity.getOrderId(), ce);
                }
            } else {
                log.error("活动抽奖中奖已落库后异常 userId:{} activityId:{} orderId:{}", userId, activityId, orderEntity.getOrderId(), e);
            }
            throw e;
        }
    }

}

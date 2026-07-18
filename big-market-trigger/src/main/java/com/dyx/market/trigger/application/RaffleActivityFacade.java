package com.dyx.market.trigger.application;

import com.dyx.market.domain.activity.service.IRaffleActivityStageService;
import com.dyx.market.domain.activity.service.armory.IActivityArmory;
import com.dyx.market.domain.strategy.service.armory.IStrategyArmory;
import com.dyx.market.trigger.api.dto.*;
import com.dyx.market.types.enums.ResponseCode;
import com.dyx.market.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.math.BigDecimal;
import java.util.List;

/**
 * 抽奖活动门面：统一编排活动装配、抽奖、签到、账户查询与积分兑换等用例。
 */
@Slf4j
@Service
public class RaffleActivityFacade {

    @Resource
    private IRaffleActivityStageService raffleActivityStageService;
    @Resource
    private IActivityArmory activityArmory;
    @Resource
    private IStrategyArmory strategyArmory;
    @Resource
    private RaffleDrawApplicationService raffleDrawApplicationService;
    @Resource
    private CalendarSignApplicationService calendarSignApplicationService;
    @Resource
    private RaffleActivityQueryApplicationService raffleActivityQueryApplicationService;
    @Resource
    private CreditPayExchangeApplicationService creditPayExchangeApplicationService;
    @Resource
    private ChatCreditApplicationService chatCreditApplicationService;

    public Long queryStageActivityId(String channel, String source) {
        Long activityId = raffleActivityStageService.queryStageActivityId(channel, source);
        log.info("查询上架活动ID channel:{} source:{} activity:{}", channel, source, activityId);
        return activityId;
    }

    public Boolean armory(Long activityId) {
        log.info("活动装配，数据预热，开始 activityId:{}", activityId);
        if (null == activityId) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), ResponseCode.ILLEGAL_PARAMETER.getInfo());
        }
        activityArmory.assembleActivitySkuByActivityId(activityId);
        strategyArmory.assembleLotteryStrategyByActivityId(activityId);
        log.info("活动装配，数据预热，完成 activityId:{}", activityId);
        return true;
    }

    public ActivityDrawResponseDTO draw(ActivityDrawRequestDTO request) {
        return raffleDrawApplicationService.draw(request);
    }

    public SignInResponseDTO calendarSignRebate(String userId) {
        log.info("执行签到开始 userId:{}", userId);
        return calendarSignApplicationService.sign(userId);
    }

    public Boolean isCalendarSignRebate(String userId) {
        log.info("执行判断签到开始 userId:{}", userId);
        return calendarSignApplicationService.isSignedToday(userId);
    }

    public UserActivityAccountResponseDTO queryUserActivityAccount(UserActivityAccountRequestDTO request) {
        return raffleActivityQueryApplicationService.queryUserActivityAccount(request);
    }

    public List<SkuProductResponseDTO> querySkuProductListByActivityId(Long activityId) {
        return raffleActivityQueryApplicationService.querySkuProductListByActivityId(activityId);
    }

    public BigDecimal queryUserCreditAccount(String userId) {
        log.info("查询用户积分值开始 userId:{}", userId);
        return raffleActivityQueryApplicationService.queryUserCreditAccount(userId);
    }

    public Boolean creditPayExchangeSku(SkuProductShopCartRequestDTO request) {
        creditPayExchangeApplicationService.creditPayExchange(request);
        return true;
    }

    public BigDecimal chatCreditRefund(String userId, String originalRequestId) {
        log.info("AI Chat积分退还开始 userId:{} requestId:{}", userId, originalRequestId);
        return chatCreditApplicationService.refund(userId, originalRequestId);
    }

    public BigDecimal chatCreditDeduct(String userId, int amount, String requestId) {
        log.info("AI Chat积分扣减开始 userId:{} amount:{} requestId:{}", userId, amount, requestId);
        return chatCreditApplicationService.deduct(userId, amount, requestId);
    }

    public Boolean chatCreditMarkRefundPending(String userId, String requestId) {
        chatCreditApplicationService.markRefundPending(userId, requestId, 0);
        return true;
    }
}

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

    /** 查询渠道当前上架的活动 ID。 */
    public Long queryStageActivityId(String channel, String source) {
        Long activityId = raffleActivityStageService.queryStageActivityId(channel, source);
        log.info("查询上架活动ID channel:{} source:{} activity:{}", channel, source, activityId);
        return activityId;
    }

    /** 预热活动 SKU 与抽奖策略缓存；重复装配是安全的。 */
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

    /** 执行一次鉴权后的抽奖用例。 */
    public ActivityDrawResponseDTO draw(ActivityDrawRequestDTO request) {
        return raffleDrawApplicationService.draw(request);
    }

    /** 执行当日签到并创建返利订单。 */
    public SignInResponseDTO calendarSignRebate(String userId) {
        log.info("执行签到开始 userId:{}", userId);
        return calendarSignApplicationService.sign(userId);
    }

    /** 查询用户当天是否已经签到。 */
    public Boolean isCalendarSignRebate(String userId) {
        log.info("执行判断签到开始 userId:{}", userId);
        return calendarSignApplicationService.isSignedToday(userId);
    }

    /** 查询用户在活动下的总/月/日剩余额度。 */
    public UserActivityAccountResponseDTO queryUserActivityAccount(UserActivityAccountRequestDTO request) {
        return raffleActivityQueryApplicationService.queryUserActivityAccount(request);
    }

    /** 查询活动可兑换 SKU。 */
    public List<SkuProductResponseDTO> querySkuProductListByActivityId(Long activityId) {
        return raffleActivityQueryApplicationService.querySkuProductListByActivityId(activityId);
    }

    /** 查询用户积分余额。 */
    public BigDecimal queryUserCreditAccount(String userId) {
        log.info("查询用户积分值开始 userId:{}", userId);
        return raffleActivityQueryApplicationService.queryUserCreditAccount(userId);
    }

    /** 使用积分兑换 SKU，requestId 负责请求幂等。 */
    public Boolean creditPayExchangeSku(SkuProductShopCartRequestDTO request) {
        creditPayExchangeApplicationService.creditPayExchange(request);
        return true;
    }

    /** 按原始聊天 requestId 发起积分退款。 */
    public BigDecimal chatCreditRefund(String userId, String originalRequestId) {
        log.info("AI Chat积分退还开始 userId:{} requestId:{}", userId, originalRequestId);
        return chatCreditApplicationService.refund(userId, originalRequestId);
    }

    /** 按聊天 requestId 扣减积分，重复请求不会重复扣费。 */
    public BigDecimal chatCreditDeduct(String userId, int amount, String requestId) {
        log.info("AI Chat积分扣减开始 userId:{} amount:{} requestId:{}", userId, amount, requestId);
        return chatCreditApplicationService.deduct(userId, amount, requestId);
    }

    /** 标记聊天退款待补偿，供 message-job 后续重试。 */
    public Boolean chatCreditMarkRefundPending(String userId, String requestId) {
        chatCreditApplicationService.markRefundPending(userId, requestId, 0);
        return true;
    }
}

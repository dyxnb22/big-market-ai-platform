package com.dyx.market.trigger.api;

import com.dyx.market.trigger.api.dto.*;
import com.dyx.market.trigger.api.response.Response;

import java.math.BigDecimal;
import java.util.List;

/**
 * @author Fuzhengwei bugstack.cn @小傅哥
 * @description 抽奖活动服务
 * @create 2024-04-13 09:16
 */
public interface IRaffleActivityService {

    /**
     * 根据sc值，查询上架的活动ID
     *
     * @param channel 渠道
     * @param source  来源
     * @return 活动ID
     */
    Response<Long> queryStageActivityId(String channel, String source);

    /**
     * 活动装配，数据预热缓存
     *
     * @param activityId 活动ID
     * @return 装配结果
     */
    Response<Boolean> armory(Long activityId);

    /**
     * 活动抽奖接口
     *
     * @param request 请求对象
     * @return 返回结果
     */
    Response<ActivityDrawResponseDTO> draw(String token, ActivityDrawRequestDTO request);

    /**
     * 活动抽奖接口
     *
     * @param request 请求对象
     * @return 返回结果
     */
    Response<ActivityDrawResponseDTO> draw(ActivityDrawRequestDTO request);

    /**
     * 日历签到返利接口（含积分余额和签到奖励）
     *
     * @param token 登录Token
     * @return 签到结果（signedToday, rewardCredit, creditBalance, message）
     */
    Response<SignInResponseDTO> calendarSignRebateByToken(String token);

    /**
     * 日历签到返利接口（含积分余额和签到奖励）
     *
     * @param userId 用户ID
     * @return 签到结果（signedToday, rewardCredit, creditBalance, message）
     */
    Response<SignInResponseDTO> calendarSignRebate(String userId);

    /**
     * 判断是否完成日历签到返利接口
     *
     * @param token token
     * @return 签到结果 true 已签到，false 未签到
     */
    Response<Boolean> isCalendarSignRebateByToken(String token);

    /**
     * 判断是否完成日历签到返利接口
     *
     * @param userId 用户ID
     * @return 签到结果 true 已签到，false 未签到
     */
    Response<Boolean> isCalendarSignRebate(String userId);

    /**
     * 查询用户活动账户
     *
     * @param token   鉴权token
     * @param request 请求对象「活动ID、用户ID」
     * @return 返回结果「总额度、月额度、日额度」
     */
    Response<UserActivityAccountResponseDTO> queryUserActivityAccount(String token, UserActivityAccountRequestDTO request);

    /**
     * 查询用户活动账户
     *
     * @param request 请求对象「活动ID、用户ID」
     * @return 返回结果「总额度、月额度、日额度」
     */
    Response<UserActivityAccountResponseDTO> queryUserActivityAccount(UserActivityAccountRequestDTO request);

    /**
     * 查询sku商品集合
     *
     * @param activityId 活动ID
     * @return 商品集合
     */
    Response<List<SkuProductResponseDTO>> querySkuProductListByActivityId(Long activityId);

    /**
     * 根据登录 Token 查询当前用户积分余额。
     *
     * @param token 登录 Token
     * @return 可用积分
     */
    Response<BigDecimal> queryUserCreditAccountByToken(String token);

    /**
     * 查询用户积分值
     *
     * @param userId 用户ID
     * @return 可用积分
     */
    Response<BigDecimal> queryUserCreditAccount(String userId);

    /**
     * 查询用户中奖记录（服务端抽奖历史，按中奖时间倒序）
     *
     * @param token 登录Token
     * @return 中奖记录集合
     */
    Response<List<UserAwardRecordResponseDTO>> queryUserAwardRecordsByToken(String token);

    /**
     * 查询用户中奖记录（服务端抽奖历史，按中奖时间倒序）
     *
     * @param userId 用户ID
     * @return 中奖记录集合
     */
    Response<List<UserAwardRecordResponseDTO>> queryUserAwardRecords(String userId);

    /**
     * 查询用户积分流水（服务端积分账本，按交易时间倒序）
     *
     * @param token 登录Token
     * @return 积分流水集合
     */
    Response<List<CreditOrderResponseDTO>> queryUserCreditOrdersByToken(String token);

    /**
     * 查询用户积分流水（服务端积分账本，按交易时间倒序）
     *
     * @param userId 用户ID
     * @return 积分流水集合
     */
    Response<List<CreditOrderResponseDTO>> queryUserCreditOrders(String userId);

    /**
     * 根据登录 Token 发起积分兑换商品。
     *
     * @param token 登录 Token
     * @param request 请求对象，包含 SKU 和请求幂等 ID
     * @return 兑换受理结果
     */
    Response<Boolean> creditPayExchangeSku(String token, SkuProductShopCartRequestDTO request);

    /**
     * 积分支付兑换商品
     *
     * @param request 请求对象「用户ID、商品ID」
     * @return 兑换结果
     */
    Response<Boolean> creditPayExchangeSku(SkuProductShopCartRequestDTO request);

    /**
     * AI Chat 积分扣减（由 chatbot-service 通过网关调用）
     *
     * @param token    用户Token
     * @param amount   扣减积分数量
     * @param requestId 幂等请求ID
     * @return 剩余积分
     */
    Response<BigDecimal> chatCreditDeductByToken(String token, int amount, String requestId);

}

package com.dyx.market.trigger.http;

import com.dyx.market.trigger.api.IRaffleActivityService;
import com.dyx.market.trigger.api.dto.*;
import com.dyx.market.trigger.api.response.Response;
import com.dyx.market.trigger.application.RaffleActivityFacade;
import com.dyx.market.trigger.support.AuthenticatedUserSupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.math.BigDecimal;
import java.util.List;

/**
 * 抽奖活动 HTTP 适配层：鉴权、参数绑定与响应包装；业务编排见 {@link RaffleActivityFacade}。
 */
@Slf4j
@RestController()
@CrossOrigin("${app.config.cross-origin}")
@RequestMapping("/api/${app.config.api-version}/raffle/activity/")
public class RaffleActivityController implements IRaffleActivityService {

    @Resource
    private RaffleActivityFacade raffleActivityFacade;
    @Resource
    private AuthenticatedUserSupport authenticatedUserSupport;

    @GetMapping("query_stage_activity_id")
    @Override
    public Response<Long> queryStageActivityId(@RequestParam String channel, @RequestParam String source) {
        return TriggerApiResponses.ok(raffleActivityFacade.queryStageActivityId(channel, source));
    }

    @GetMapping("armory")
    @Override
    public Response<Boolean> armory(@RequestParam Long activityId) {
        return TriggerApiResponses.ok(raffleActivityFacade.armory(activityId));
    }

    @PostMapping("draw_by_token")
    @Override
    public Response<ActivityDrawResponseDTO> draw(@RequestHeader("Authorization") String token,
                                                    @RequestBody ActivityDrawRequestDTO request) {
        request.setUserId(authenticatedUserSupport.requireUserId(token));
        return draw(request);
    }

    @Override
    public Response<ActivityDrawResponseDTO> draw(@RequestBody ActivityDrawRequestDTO request) {
        return TriggerApiResponses.ok(raffleActivityFacade.draw(request));
    }

    @PostMapping("calendar_sign_rebate_by_token")
    @Override
    public Response<SignInResponseDTO> calendarSignRebateByToken(@RequestHeader("Authorization") String token) {
        return calendarSignRebate(authenticatedUserSupport.requireUserId(token));
    }

    @Override
    public Response<SignInResponseDTO> calendarSignRebate(String userId) {
        return TriggerApiResponses.ok(raffleActivityFacade.calendarSignRebate(userId));
    }

    @PostMapping("is_calendar_sign_rebate_by_token")
    @Override
    public Response<Boolean> isCalendarSignRebateByToken(@RequestHeader("Authorization") String token) {
        return isCalendarSignRebate(authenticatedUserSupport.requireUserId(token));
    }

    @Override
    public Response<Boolean> isCalendarSignRebate(String userId) {
        return TriggerApiResponses.ok(raffleActivityFacade.isCalendarSignRebate(userId));
    }

    @PostMapping("query_user_activity_account_by_token")
    @Override
    public Response<UserActivityAccountResponseDTO> queryUserActivityAccount(
            @RequestHeader("Authorization") String token,
            @RequestBody UserActivityAccountRequestDTO request) {
        request.setUserId(authenticatedUserSupport.requireUserId(token));
        return queryUserActivityAccount(request);
    }

    @Override
    public Response<UserActivityAccountResponseDTO> queryUserActivityAccount(
            @RequestBody UserActivityAccountRequestDTO request) {
        return TriggerApiResponses.ok(raffleActivityFacade.queryUserActivityAccount(request));
    }

    @PostMapping("query_sku_product_list_by_activity_id")
    @Override
    public Response<List<SkuProductResponseDTO>> querySkuProductListByActivityId(
            @RequestParam("activityId") Long activityId) {
        return TriggerApiResponses.ok(raffleActivityFacade.querySkuProductListByActivityId(activityId));
    }

    @PostMapping("query_user_credit_account_by_token")
    @Override
    public Response<BigDecimal> queryUserCreditAccountByToken(@RequestHeader("Authorization") String token) {
        return queryUserCreditAccount(authenticatedUserSupport.requireUserId(token));
    }

    @Override
    public Response<BigDecimal> queryUserCreditAccount(String userId) {
        return TriggerApiResponses.ok(raffleActivityFacade.queryUserCreditAccount(userId));
    }

    @PostMapping("query_user_award_record_by_token")
    @Override
    public Response<List<UserAwardRecordResponseDTO>> queryUserAwardRecordsByToken(
            @RequestHeader("Authorization") String token) {
        return queryUserAwardRecords(authenticatedUserSupport.requireUserId(token));
    }

    @Override
    public Response<List<UserAwardRecordResponseDTO>> queryUserAwardRecords(String userId) {
        return TriggerApiResponses.ok(raffleActivityFacade.queryUserAwardRecords(userId));
    }

    @PostMapping("query_user_credit_order_by_token")
    @Override
    public Response<List<CreditOrderResponseDTO>> queryUserCreditOrdersByToken(
            @RequestHeader("Authorization") String token) {
        return queryUserCreditOrders(authenticatedUserSupport.requireUserId(token));
    }

    @Override
    public Response<List<CreditOrderResponseDTO>> queryUserCreditOrders(String userId) {
        return TriggerApiResponses.ok(raffleActivityFacade.queryUserCreditOrders(userId));
    }

    @PostMapping("credit_pay_exchange_sku_by_token")
    @Override
    public Response<Boolean> creditPayExchangeSku(@RequestHeader("Authorization") String token,
                                                  @RequestBody SkuProductShopCartRequestDTO request) {
        request.setUserId(authenticatedUserSupport.requireUserId(token));
        return creditPayExchangeSku(request);
    }

    @Override
    public Response<Boolean> creditPayExchangeSku(@RequestBody SkuProductShopCartRequestDTO request) {
        return TriggerApiResponses.ok(raffleActivityFacade.creditPayExchangeSku(request));
    }

    @PostMapping("chat_credit_deduct_by_token")
    @Override
    public Response<BigDecimal> chatCreditDeductByToken(@RequestHeader("Authorization") String token,
                                                        @RequestParam(defaultValue = "1") int amount,
                                                        @RequestParam String requestId) {
        return TriggerApiResponses.ok(raffleActivityFacade.chatCreditDeduct(
                authenticatedUserSupport.requireUserId(token), amount, requestId));
    }
}

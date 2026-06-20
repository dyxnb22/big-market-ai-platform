package com.dyx.market.trigger.rpc;

import com.dyx.market.trigger.api.IRaffleActivityService;
import com.dyx.market.trigger.api.dto.*;
import com.dyx.market.trigger.api.response.Response;
import com.dyx.market.trigger.application.RaffleActivityFacade;
import com.dyx.market.trigger.http.TriggerApiResponses;
import com.dyx.market.trigger.support.AuthenticatedUserSupport;
import com.dyx.market.trigger.support.DubboRpcAuthSupport;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.List;

@DubboService(version = "1.0")
@ConditionalOnProperty(name = "activity.embedded-rpc-provider.enabled", havingValue = "true", matchIfMissing = true)
public class RaffleActivityServiceRPC implements IRaffleActivityService {

    @Resource
    private RaffleActivityFacade raffleActivityFacade;
    @Resource
    private AuthenticatedUserSupport authenticatedUserSupport;

    @Override
    public Response<Long> queryStageActivityId(String channel, String source) {
        return TriggerApiResponses.ok(raffleActivityFacade.queryStageActivityId(channel, source));
    }

    @Override
    public Response<Boolean> armory(Long activityId) {
        DubboRpcAuthSupport.rejectInternalRpc("armory");
        return null;
    }

    @Override
    public Response<ActivityDrawResponseDTO> draw(String token, ActivityDrawRequestDTO request) {
        request.setUserId(authenticatedUserSupport.requireUserId(token));
        return TriggerApiResponses.ok(raffleActivityFacade.draw(request));
    }

    @Override
    public Response<ActivityDrawResponseDTO> draw(ActivityDrawRequestDTO request) {
        DubboRpcAuthSupport.rejectInternalRpc("draw");
        return null;
    }

    @Override
    public Response<SignInResponseDTO> calendarSignRebateByToken(String token) {
        return TriggerApiResponses.ok(raffleActivityFacade.calendarSignRebate(
                authenticatedUserSupport.requireUserId(token)));
    }

    @Override
    public Response<SignInResponseDTO> calendarSignRebate(String userId) {
        DubboRpcAuthSupport.rejectInternalRpc("calendarSignRebate");
        return null;
    }

    @Override
    public Response<Boolean> isCalendarSignRebateByToken(String token) {
        return TriggerApiResponses.ok(raffleActivityFacade.isCalendarSignRebate(
                authenticatedUserSupport.requireUserId(token)));
    }

    @Override
    public Response<Boolean> isCalendarSignRebate(String userId) {
        DubboRpcAuthSupport.rejectInternalRpc("isCalendarSignRebate");
        return null;
    }

    @Override
    public Response<UserActivityAccountResponseDTO> queryUserActivityAccount(
            String token, UserActivityAccountRequestDTO request) {
        request.setUserId(authenticatedUserSupport.requireUserId(token));
        return TriggerApiResponses.ok(raffleActivityFacade.queryUserActivityAccount(request));
    }

    @Override
    public Response<UserActivityAccountResponseDTO> queryUserActivityAccount(UserActivityAccountRequestDTO request) {
        DubboRpcAuthSupport.rejectInternalRpc("queryUserActivityAccount");
        return null;
    }

    @Override
    public Response<List<SkuProductResponseDTO>> querySkuProductListByActivityId(Long activityId) {
        return TriggerApiResponses.ok(raffleActivityFacade.querySkuProductListByActivityId(activityId));
    }

    @Override
    public Response<BigDecimal> queryUserCreditAccountByToken(String token) {
        return TriggerApiResponses.ok(raffleActivityFacade.queryUserCreditAccount(
                authenticatedUserSupport.requireUserId(token)));
    }

    @Override
    public Response<BigDecimal> queryUserCreditAccount(String userId) {
        DubboRpcAuthSupport.rejectInternalRpc("queryUserCreditAccount");
        return null;
    }

    @Override
    public Response<Boolean> creditPayExchangeSku(String token, SkuProductShopCartRequestDTO request) {
        request.setUserId(authenticatedUserSupport.requireUserId(token));
        return TriggerApiResponses.ok(raffleActivityFacade.creditPayExchangeSku(request));
    }

    @Override
    public Response<Boolean> creditPayExchangeSku(SkuProductShopCartRequestDTO request) {
        DubboRpcAuthSupport.rejectInternalRpc("creditPayExchangeSku");
        return null;
    }

    @Override
    public Response<BigDecimal> chatCreditDeductByToken(String token, int amount, String requestId) {
        return TriggerApiResponses.ok(raffleActivityFacade.chatCreditDeduct(
                authenticatedUserSupport.requireUserId(token), amount, requestId));
    }

    @Override
    public Response<BigDecimal> chatCreditRefundByToken(String token, int amount, String originalRequestId) {
        return TriggerApiResponses.ok(raffleActivityFacade.chatCreditRefund(
                authenticatedUserSupport.requireUserId(token), amount, originalRequestId));
    }
}

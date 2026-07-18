package com.dyx.market.trigger.http;

import com.dyx.market.trigger.api.response.Response;
import com.dyx.market.trigger.application.RaffleActivityFacade;
import com.dyx.market.trigger.support.AuthenticatedUserSupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.math.BigDecimal;

/**
 * Chat 积分内部接口：仅 chatbot / message-job 经服务令牌调用，不对公网用户暴露退款面。
 */
@Slf4j
@RestController
@RequestMapping("/api/${app.config.api-version}/internal/raffle/activity/")
public class InternalChatCreditController {

    @Resource
    private RaffleActivityFacade raffleActivityFacade;
    @Resource
    private AuthenticatedUserSupport authenticatedUserSupport;

    @PostMapping("chat_credit_refund_by_token")
    public Response<BigDecimal> chatCreditRefundByToken(@RequestHeader("Authorization") String token,
                                                        @RequestParam String originalRequestId) {
        return TriggerApiResponses.ok(raffleActivityFacade.chatCreditRefund(
                authenticatedUserSupport.requireUserId(token), originalRequestId));
    }

    @PostMapping("chat_credit_mark_refund_pending_by_token")
    public Response<Boolean> chatCreditMarkRefundPendingByToken(@RequestHeader("Authorization") String token,
                                                                @RequestParam String requestId) {
        String userId = authenticatedUserSupport.requireUserId(token);
        raffleActivityFacade.chatCreditMarkRefundPending(userId, requestId);
        return TriggerApiResponses.ok(true);
    }
}

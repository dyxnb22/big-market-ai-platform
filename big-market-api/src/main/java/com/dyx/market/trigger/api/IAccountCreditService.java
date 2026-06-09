package com.dyx.market.trigger.api;

import com.dyx.market.trigger.api.dto.CreditTradeRequestDTO;
import com.dyx.market.trigger.api.response.Response;

import java.math.BigDecimal;

/**
 * Cross-service Dubbo API for credit account operations.
 *
 * Dark-launch Phase 2.2-A: interface declared here; provider lives in
 * big-market-account-service. Existing callers in market-service/message-job-service
 * still call domain services in-process — no traffic cutover yet.
 */
public interface IAccountCreditService {

    /**
     * Create a credit trade order (earn or spend credit).
     *
     * @param request trade parameters — userId, tradeName, tradeType, amount, outBusinessNo
     * @return orderId on success
     */
    Response<String> createOrder(CreditTradeRequestDTO request);

    /**
     * Query a user's current available credit balance.
     *
     * @param userId user identifier
     * @return available credit amount
     */
    Response<BigDecimal> queryUserCreditAccount(String userId);

}

package com.dyx.market.trigger.adapter;

import com.dyx.market.domain.credit.model.entity.TradeEntity;

/**
 * Credit write routing adapter — Phase 2.2-B2/B3.
 *
 * Wired callers (Phase 2.2-B2/B3):
 *   - RebateMessageConsumer.createOrder for integral  (message-job-service)
 *   - RaffleActivityController.creditPayExchangeSku createOrder (market-service, Phase 2.2-B3)
 *
 * Still pending wiring:
 *   - UserCreditRandomAward (award credit path) — needs call-chain audit before wiring
 *
 * Default behavior: local domain service (flag=false). Remote Dubbo call active only when
 * account.service.remote-credit-write.enabled=true.
 */
public interface IAccountCreditWriteAdapter {

    String createOrder(TradeEntity tradeEntity);

}

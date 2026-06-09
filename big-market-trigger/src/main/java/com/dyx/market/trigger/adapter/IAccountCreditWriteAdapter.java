package com.dyx.market.trigger.adapter;

import com.dyx.market.domain.credit.model.entity.TradeEntity;

/**
 * Phase 2.2-B2 scaffold for credit write routing.
 *
 * Callers are not wired to this adapter yet; local domain services remain the
 * default write path until account.service.remote-credit-write.enabled is
 * deliberately enabled in a later cutover batch.
 */
public interface IAccountCreditWriteAdapter {

    String createOrder(TradeEntity tradeEntity);

}

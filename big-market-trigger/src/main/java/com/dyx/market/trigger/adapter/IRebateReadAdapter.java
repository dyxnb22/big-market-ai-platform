package com.dyx.market.trigger.adapter;

/**
 * Routes rebate order read queries to either the local domain service (default) or
 * big-market-rebate-service via Dubbo, controlled by rebate.service.remote-read.enabled.
 *
 * rebate read adapter boundary. Default: local domain service (flag=false).
 * Remote Dubbo call active only when rebate.service.remote-read.enabled=true.
 * Do not enable that flag until:
 *   - duplicate IRebateService provider risk is resolved (default RPC provider disabled)
 *   - local validation for big-market-rebate-service passes
 */
public interface IRebateReadAdapter {

    boolean isCalendarSignRebate(String userId, String outBusinessNo);

}

package com.dyx.market.management.config;

/**
 * Optional bridge to runtime dynamic config (e.g. ZK DCC via market-service HTTP).
 */
public interface DynamicConfigSyncPort {

    /**
     * Push {@code system.degradeSwitch} to the live DCC source so raffle paths pick it up without restart.
     */
    void syncDegradeSwitch(String value);

    /**
     * Push {@code system.rateLimiterSwitch} to DCC for {@link com.dyx.market.starter.ratelimiter.RateLimiterAspect}.
     */
    void syncRateLimiterSwitch(String value);
}

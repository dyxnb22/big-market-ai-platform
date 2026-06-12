package com.dyx.market.market.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Startup validator that prevents dangerous dual-path configurations.
 *
 * If BOTH a remote/cutover flag AND its corresponding legacy provider/fallback
 * flag are enabled simultaneously, the application refuses to start because
 * this would cause double-writes, double-dispatch, or duplicate provider
 * registration.
 *
 * SAFETY: Read-only check. Never modifies config.
 * ROLLBACK: Set FLAG_MUTUAL_EXCLUSION_GUARD_ENABLED=false to disable.
 */
@Slf4j
@Component
public class FlagMutualExclusionValidator implements CommandLineRunner {

    @Value("${flag-mutual-exclusion-guard.enabled:true}")
    private boolean guardEnabled;

    // Account credit write
    @Value("${account.service.remote-credit-write.enabled:false}")
    private boolean remoteCreditWrite;
    // Legacy credit write is always local until remote is cut over;
    // guarding against ACCOUNT_SERVICE_REMOTE_QUOTA_WRITE_ENABLED + legacy

    // Account quota write
    @Value("${account.service.remote-quota-write.enabled:false}")
    private boolean remoteQuotaWrite;

    // Account quota decrement
    @Value("${account.service.remote-quota-decrement.enabled:false}")
    private boolean remoteQuotaDecrement;

    // Fulfillment
    @Value("${account.fulfillment.remote-award.enabled:false}")
    private boolean remoteAward;

    // Rebate
    @Value("${rebate.service.remote-create-order.enabled:false}")
    private boolean rebateRemoteCreateOrder;
    @Value("${rebate.legacy-rpc-provider.enabled:true}")
    private boolean rebateLegacyProvider;

    // Strategy
    @Value("${strategy.service.remote-read.enabled:false}")
    private boolean strategyRemoteRead;
    @Value("${strategy.legacy-rpc-provider.enabled:true}")
    private boolean strategyLegacyProvider;

    @Override
    public void run(String... args) {
        if (!guardEnabled) {
            log.warn("[FlagMutualExclusionValidator] DISABLED — dual-path conflicts will not be caught");
            return;
        }

        List<String> violations = new ArrayList<>();

        // Rebate: remote create-order + legacy provider = duplicate provider risk
        if (rebateRemoteCreateOrder && rebateLegacyProvider) {
            violations.add("rebate.service.remote-create-order.enabled=true AND "
                    + "rebate.legacy-rpc-provider.enabled=true — duplicate IRebateService provider risk. "
                    + "Disable the legacy provider before enabling remote rebate.");
        }

        // Strategy: remote read + legacy provider = duplicate provider risk
        if (strategyRemoteRead && strategyLegacyProvider) {
            violations.add("strategy.service.remote-read.enabled=true AND "
                    + "strategy.legacy-rpc-provider.enabled=true — duplicate IRaffleStrategyService provider risk. "
                    + "Disable the legacy provider before enabling remote strategy read.");
        }

        // Quota: remote decrement + remote quota write both on = unclear path
        if (remoteQuotaDecrement && remoteQuotaWrite) {
            log.warn("[FlagMutualExclusionValidator] remote-quota-decrement AND remote-quota-write "
                    + "both enabled — ensure only one path is active per quota operation");
        }

        if (!violations.isEmpty()) {
            String msg = "\n==========================================================\n"
                    + "[FlagMutualExclusionValidator] REFUSING TO START — dangerous "
                    + "dual-path flag configuration detected:\n"
                    + String.join("\n", violations)
                    + "\n\nSet FLAG_MUTUAL_EXCLUSION_GUARD_ENABLED=false only when "
                    + "you are certain this is intentional.\n"
                    + "==========================================================";
            log.error(msg);
            throw new IllegalStateException(msg);
        }

        log.info("[FlagMutualExclusionValidator] no dual-path conflicts detected");
    }
}

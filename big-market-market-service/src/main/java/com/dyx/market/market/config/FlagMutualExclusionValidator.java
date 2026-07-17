package com.dyx.market.market.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * 启动期账户配额路径校验。
 * <p>
 * 远程配额写与远程配额扣减可以分别用于不同业务操作，但同时启用时需要确认
 * 每条操作只走一条路径，避免重复扣减。
 * <p>
 * 仅做只读检查，不修改配置。可通过 {@code flag-mutual-exclusion-guard.enabled=false} 关闭。
 */
@Slf4j
@Component
public class FlagMutualExclusionValidator implements CommandLineRunner {

    @Value("${flag-mutual-exclusion-guard.enabled:true}")
    private boolean guardEnabled;

    // 账户配额写
    @Value("${account.service.remote-quota-write.enabled:false}")
    private boolean remoteQuotaWrite;

    // 账户配额扣减
    @Value("${account.service.remote-quota-decrement.enabled:false}")
    private boolean remoteQuotaDecrement;

    @Override
    public void run(String... args) {
        if (!guardEnabled) {
            log.warn("[FlagMutualExclusionValidator] DISABLED — dual-path conflicts will not be caught");
            return;
        }

        // 配额：远程扣减与远程写同时开启时告警，需确保每条操作只走一条路径
        if (remoteQuotaDecrement && remoteQuotaWrite) {
            log.warn("[FlagMutualExclusionValidator] remote-quota-decrement AND remote-quota-write "
                    + "both enabled — ensure only one path is active per quota operation");
        }

        log.info("[FlagMutualExclusionValidator] no dual-path conflicts detected");
    }
}

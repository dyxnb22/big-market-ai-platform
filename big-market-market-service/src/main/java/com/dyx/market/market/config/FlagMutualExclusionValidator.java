package com.dyx.market.market.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 启动期互斥校验：防止危险的双路径配置同时生效。
 * <p>
 * 若「远程服务路由」与「嵌入式 Provider」两类开关同时为 true，则拒绝启动，
 * 避免重复写库、重复派发或 Dubbo Provider 重复注册。
 * <p>
 * 仅做只读检查，不修改配置。可通过 {@code flag-mutual-exclusion-guard.enabled=false} 关闭。
 */
@Slf4j
@Component
public class FlagMutualExclusionValidator implements CommandLineRunner {

    @Value("${flag-mutual-exclusion-guard.enabled:true}")
    private boolean guardEnabled;

    // 账户积分写
    @Value("${account.service.remote-credit-write.enabled:false}")
    private boolean remoteCreditWrite;

    // 账户配额写
    @Value("${account.service.remote-quota-write.enabled:false}")
    private boolean remoteQuotaWrite;

    // 账户配额扣减
    @Value("${account.service.remote-quota-decrement.enabled:false}")
    private boolean remoteQuotaDecrement;

    // 发奖履约
    @Value("${account.fulfillment.remote-award.enabled:false}")
    private boolean remoteAward;

    // 返利
    @Value("${rebate.service.remote-create-order.enabled:false}")
    private boolean rebateRemoteCreateOrder;
    @Value("${rebate.embedded-rpc-provider.enabled:true}")
    private boolean rebateEmbeddedProvider;

    // 策略
    @Value("${strategy.service.remote-read.enabled:false}")
    private boolean strategyRemoteRead;
    @Value("${strategy.embedded-rpc-provider.enabled:true}")
    private boolean strategyEmbeddedProvider;

    @Override
    public void run(String... args) {
        if (!guardEnabled) {
            log.warn("[FlagMutualExclusionValidator] DISABLED — dual-path conflicts will not be caught");
            return;
        }

        List<String> violations = new ArrayList<>();

        // 返利：远程建单 + 嵌入式 Provider 同时开启 → 重复注册风险
        if (rebateRemoteCreateOrder && rebateEmbeddedProvider) {
            violations.add("rebate.service.remote-create-order.enabled=true AND "
                    + "rebate.embedded-rpc-provider.enabled=true — duplicate IRebateService provider risk. "
                    + "Run either the embedded provider or the dedicated rebate service provider.");
        }

        // 策略：远程读 + 嵌入式 Provider 同时开启 → 重复注册风险
        if (strategyRemoteRead && strategyEmbeddedProvider) {
            violations.add("strategy.service.remote-read.enabled=true AND "
                    + "strategy.embedded-rpc-provider.enabled=true — duplicate IRaffleStrategyService provider risk. "
                    + "Run either the embedded provider or the dedicated strategy service provider.");
        }

        // 配额：远程扣减与远程写同时开启时仅告警，需确保每条操作只走一条路径
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

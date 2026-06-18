package com.dyx.market.message.job.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Job 互斥校验：防止 DispatchCreditAwardTaskJob 与 SendMessageTaskJob 同时处理相同任务行。
 * <p>
 * 当 {@code account.award-credit-outbox.enabled=true} 时，DispatchCreditAwardTaskJob
 * 会扫描 credit_award_task 表；若 SendMessageTaskJob 也扫描共享 task 表并处理相同工作项，
 * 将导致重复派发。
 * <p>
 * 本校验器在 outbox 已启用但共享 task 派发未显式禁用时拒绝启动（反之亦然）。
 * <p>
 * 安全：只读检查，不修改配置。回滚：设 {@code JOB_MUTUAL_EXCLUSION_GUARD_ENABLED=false} 可禁用。
 */
@Slf4j
@Component
public class JobMutualExclusionValidator implements CommandLineRunner {

    @Value("${job-mutual-exclusion-guard.enabled:true}")
    private boolean guardEnabled;

    @Value("${account.award-credit-outbox.enabled:false}")
    private boolean awardCreditOutboxEnabled;

    /**
     * 为 true 时，共享 task 派发器（SendMessageTaskJob）不处理发奖积分任务，
     * 由专用 outbox 派发器独占。绑定 {@code job.shared-task-dispatch.credit-award-disabled}。
     */
    @Value("${job.shared-task-dispatch.credit-award-disabled:false}")
    private boolean sharedTaskCreditAwardDisabled;

    @Value("${account.service.remote-credit-write.enabled:false}")
    private boolean remoteCreditWrite;

    @Override
    public void run(String... args) {
        if (!guardEnabled) {
            log.warn("[JobMutualExclusionValidator] DISABLED — parallel dispatch conflicts will not be caught");
            return;
        }

        List<String> violations = new ArrayList<>();

        // outbox 已启用但共享 task 派发未显式禁用时，两个 Job 可能同时处理发奖积分任务
        if (awardCreditOutboxEnabled && !sharedTaskCreditAwardDisabled) {
            violations.add("account.award-credit-outbox.enabled=true BUT "
                    + "job.shared-task-dispatch.credit-award-disabled=false — "
                    + "DispatchCreditAwardTaskJob and SendMessageTaskJob may both "
                    + "process credit-award tasks. Set shared-task-dispatch.credit-award-disabled=true "
                    + "or keep outbox disabled.");
        }

        // 远程积分写已启用但 outbox 未启用时，积分写路径不完整（outbox 才是安全的异步派发机制）
        if (remoteCreditWrite && !awardCreditOutboxEnabled) {
            log.warn("[JobMutualExclusionValidator] remote-credit-write=true but "
                    + "award-credit-outbox=false — direct synchronous credit writes without "
                    + "outbox durability. Consider enabling the outbox for idempotent delivery.");
        }

        if (!violations.isEmpty()) {
            String msg = "\n==========================================================\n"
                    + "[JobMutualExclusionValidator] REFUSING TO START — "
                    + "dangerous parallel dispatch configuration detected:\n"
                    + String.join("\n", violations)
                    + "\n\nSet JOB_MUTUAL_EXCLUSION_GUARD_ENABLED=false only when "
                    + "you are certain this is intentional.\n"
                    + "==========================================================";
            log.error(msg);
            throw new IllegalStateException(msg);
        }

        log.info("[JobMutualExclusionValidator] no parallel dispatch conflicts detected");
    }
}

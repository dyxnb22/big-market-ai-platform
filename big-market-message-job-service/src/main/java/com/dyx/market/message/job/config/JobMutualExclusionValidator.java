package com.dyx.market.message.job.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Prevents DispatchCreditAwardTaskJob and SendMessageTaskJob from running
 * simultaneously when both could process the same task rows.
 *
 * When account.award-credit-outbox.enabled=true, DispatchCreditAwardTaskJob
 * scans credit_award_task tables. If SendMessageTaskJob also scans the shared
 * task table and finds the same work items, duplicate dispatch occurs.
 *
 * This validator refuses startup when the outbox is enabled but the shared
 * task fallback is not explicitly disabled, or vice versa.
 *
 * SAFETY: Read-only check. Never modifies config.
 * ROLLBACK: Set JOB_MUTUAL_EXCLUSION_GUARD_ENABLED=false to disable.
 */
@Slf4j
@Component
public class JobMutualExclusionValidator implements CommandLineRunner {

    @Value("${job-mutual-exclusion-guard.enabled:true}")
    private boolean guardEnabled;

    @Value("${account.award-credit-outbox.enabled:false}")
    private boolean awardCreditOutboxEnabled;

    /**
     * When true, the shared task table fallback dispatcher (SendMessageTaskJob)
     * should not also process credit-award tasks. This flag signals that the
     * owner is aware of the shared task table overlap.
     */
    @Value("${job.shared-task-fallback.credit-award-disabled:false}")
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

        // If outbox is enabled but shared task fallback is not explicitly disabled,
        // DispatchCreditAwardTaskJob and SendMessageTaskJob could both process
        // credit-award tasks from the shared task table.
        if (awardCreditOutboxEnabled && !sharedTaskCreditAwardDisabled) {
            violations.add("account.award-credit-outbox.enabled=true BUT "
                    + "job.shared-task-fallback.credit-award-disabled=false — "
                    + "DispatchCreditAwardTaskJob and SendMessageTaskJob may both "
                    + "process credit-award tasks. Set shared-task-fallback.credit-award-disabled=true "
                    + "or keep outbox disabled.");
        }

        // If remote credit write is enabled but outbox is not, the credit write
        // path is incomplete (outbox is the safe async dispatch mechanism).
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

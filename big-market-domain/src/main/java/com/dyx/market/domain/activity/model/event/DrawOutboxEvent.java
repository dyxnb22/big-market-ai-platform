package com.dyx.market.domain.activity.model.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * Correlation payload for the draw saga outbox port.
 *
 * carries the minimum set of identifiers needed to reconstruct
 * the in-flight draw saga step across a configured service boundary.
 *
 * orderId is the idempotency key that threads through createOrder →
 * performRaffle → saveUserAwardRecord. Any configured remote step MUST echo
 * it back so the orchestrator can detect duplicate delivery.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DrawOutboxEvent {

    private String userId;
    private Long activityId;
    private Long strategyId;
    private String orderId;
    private Integer awardId;
    private String awardTitle;
    private Date awardTime;
    private String awardConfig;
    private DrawSagaStep sagaStep;

    public enum DrawSagaStep {
        CREATE_ORDER,
        PERFORM_RAFFLE,
        SAVE_AWARD_RECORD,
        COMPLETE,
        COMPENSATE
    }
}

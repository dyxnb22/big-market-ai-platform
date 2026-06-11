package com.dyx.market.infrastructure.adapter.port;

import com.dyx.market.domain.activity.adapter.port.IDrawOutboxPort;
import com.dyx.market.domain.activity.model.event.DrawOutboxEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * Local (in-process) no-op implementation of IDrawOutboxPort.
 *
 * Phase 5-G: no additional outbox write is needed in the local path because
 * AwardRepository.saveUserAwardRecord already writes the task outbox row
 * inside the same DB transaction as user_award_record. Durability is provided
 * by that existing local transaction; this adapter merely logs the saga step
 * for observability.
 *
 * Active by default via @ConditionalOnMissingBean. A future remote
 * ActivityDrawOutboxPort (guarded by activity.service.draw-outbox.enabled=false)
 * would take precedence when that flag is true. That remote implementation is
 * NOT introduced in this batch.
 *
 * NOTE: IDrawOutboxPort is not yet injected into RaffleApplicationService.
 * This class exists as a scaffold. Wiring it into the draw hot-path requires
 * Phase 7-D outbox DDL to be applied and Phase 8-E cutover approval.
 */
@Slf4j
@Component
@ConditionalOnMissingBean(IDrawOutboxPort.class)
public class LocalDrawOutboxPort implements IDrawOutboxPort {

    @Override
    public void publishDrawSagaStep(DrawOutboxEvent event) {
        log.debug("[LocalDrawOutboxPort] saga step={} userId={} activityId={} orderId={} awardId={}",
                event.getSagaStep(), event.getUserId(), event.getActivityId(),
                event.getOrderId(), event.getAwardId());
    }
}

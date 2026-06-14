package com.dyx.market.infrastructure.adapter.port;

import com.dyx.market.domain.activity.adapter.port.IDrawOutboxPort;
import com.dyx.market.domain.activity.model.event.DrawOutboxEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * Local (in-process) no-op implementation of IDrawOutboxPort.
 *
 * no additional outbox write is needed in the local path because
 * AwardRepository.saveUserAwardRecord already writes the task outbox row
 * inside the same DB transaction as user_award_record. Durability is provided
 * by that existing local transaction; this adapter merely logs the saga step
 * for observability.
 *
 * Active by default via @ConditionalOnMissingBean. A configured remote
 * ActivityDrawOutboxPort (guarded by activity.service.draw-outbox.enabled=false)
 * would take precedence when that flag is true. That remote implementation is
 * documented extension point.
 *
 * NOTE: IDrawOutboxPort is not yet injected into RaffleApplicationService.
 * This class exists as a default implementation. Wiring it into the draw hot-path requires
 * outbox DDL to be applied and routing approval.
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

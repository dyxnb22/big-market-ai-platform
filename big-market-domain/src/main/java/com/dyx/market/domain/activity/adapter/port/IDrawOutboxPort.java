package com.dyx.market.domain.activity.adapter.port;

import com.dyx.market.domain.activity.model.event.DrawOutboxEvent;

/**
 * Domain port for the draw saga outbox publication step.
 *
 * Phase 5-G contract.
 *
 * Design rationale:
 *   The draw orchestration in RaffleApplicationService currently persists the
 *   award record and a task outbox row in a single local DB transaction inside
 *   AwardRepository.saveUserAwardRecord. As long as all three orchestration steps
 *   remain in-process, this local transaction is sufficient.
 *
 *   When any step moves cross-service in Phase 8-E, a durable outbox is required
 *   so that in-flight draw sagas survive process restarts. This port is the
 *   routing seam that allows the outbox publication step to be swapped between:
 *
 *   Local path (default, Phase 5-G):
 *     LocalDrawOutboxPort — no-op because the shared task table outbox already
 *     provides durability for the current in-process path. Logging only.
 *
 *   Remote path (future, NOT introduced in this batch):
 *     A future ActivityDrawOutboxPort would write a row to an activity-owned
 *     draw_saga_outbox table (proposed DDL lives in docs/sql/).
 *     It must not be introduced until:
 *       - Phase 7-D activity outbox DDL is applied by the DBA.
 *       - Phase 8-E cutover approval gate is passed.
 *       - activity.service.draw-outbox.enabled defaults false and is only
 *         flipped by an explicit cutover batch backed by staging evidence.
 *
 * Usage:
 *   This port is NOT yet injected into RaffleApplicationService.
 *   It is introduced as a scaffold contract so that Phase 7-D and Phase 8-E
 *   can be planned and validated against a concrete interface.
 */
public interface IDrawOutboxPort {

    /**
     * Publish a draw saga step event to the durable outbox.
     *
     * In the local path this is a no-op (logging only); the shared task table
     * inside AwardRepository already provides durability. In the future remote
     * path this would write a draw_saga_outbox row inside the same DB transaction
     * as the step's primary write.
     *
     * @param event draw outbox event carrying the correlation key (orderId) and
     *              the saga step identifier
     */
    void publishDrawSagaStep(DrawOutboxEvent event);
}

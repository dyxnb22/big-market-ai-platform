package com.dyx.market.domain.activity.adapter.port;

import com.dyx.market.domain.activity.model.event.DrawOutboxEvent;

/**
 * Domain port for the draw saga outbox publication step.
 *
 * Domain contract for optional draw-saga outbox publication.
 *
 * Design rationale:
 *   The draw orchestration in RaffleApplicationService currently persists the
 *   award record and a task outbox row in a single local DB transaction inside
 *   AwardRepository.saveUserAwardRecord. As long as all three orchestration steps
 *   remain in-process, this local transaction is sufficient.
 *
 *   When a draw step is executed across service boundaries, a durable outbox is required
 *   so that in-flight draw sagas survive process restarts. This port is the
 *   routing seam that allows the outbox publication step to be swapped between:
 *
 *   Local path (default):
 *     LocalDrawOutboxPort — no-op because the shared task table outbox already
 *     provides durability for the current in-process path. Logging only.
 *
 *   Remote path (documented extension point):
 *     A configured ActivityDrawOutboxPort would write a row to an activity-owned
 *     draw_saga_outbox table (learning DDL lives in docs/sql/).
 *     The local learning project keeps this as a documented extension point.
 *
 * Usage:
 *   This port is not injected into RaffleApplicationService in the current
 *   local learning path. It documents the boundary for a durable draw outbox.
 */
public interface IDrawOutboxPort {

    /**
     * Publish a draw saga step event to the durable outbox.
     *
     * In the local path this is a no-op (logging only); the shared task table
     * inside AwardRepository already provides durability. In the configured remote
     * path this writes a draw_saga_outbox row inside the same DB transaction
     * as the step's primary write.
     *
     * @param event draw outbox event carrying the correlation key (orderId) and
     *              the saga step identifier
     */
    void publishDrawSagaStep(DrawOutboxEvent event);
}

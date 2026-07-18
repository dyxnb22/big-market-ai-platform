package com.dyx.market.domain.activity.adapter.port;

/**
 * Remote-write reconciliation outbox port.
 *
 * <p>Callers provide the original business key and request payload when an RPC
 * result is unknown. The infrastructure implementation persists it on the
 * user's shard for the reconcile job; callers must not depend on a DAO or an
 * infrastructure implementation directly.</p>
 */
public interface IPendingRemoteWritePort {

    boolean enqueue(String outBusinessNo, String operation, Object payload, String userId);

    boolean enqueue(String outBusinessNo, String operation, Object payload);

}

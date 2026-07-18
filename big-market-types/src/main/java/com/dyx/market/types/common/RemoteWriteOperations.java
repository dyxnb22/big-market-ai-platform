package com.dyx.market.types.common;

/**
 * {@code pending_remote_write_task.operation} 常量。
 */
public final class RemoteWriteOperations {

    public static final String CREDIT_CREATE = "credit_create";
    public static final String QUOTA_CREATE = "quota_create";
    public static final String QUOTA_UPDATE = "quota_update";
    public static final String QUOTA_ROLLBACK = "quota_rollback";

    private RemoteWriteOperations() {
    }
}

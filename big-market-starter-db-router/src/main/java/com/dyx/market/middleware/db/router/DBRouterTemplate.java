package com.dyx.market.middleware.db.router;

import com.dyx.market.middleware.db.router.strategy.IDBRouterStrategy;

import java.util.function.Supplier;

/**
 * ThreadLocal shard routing helper: set route, run work, always clear (or restore).
 *
 * <p>Prefer this over manual {@code doRouter}/{@code setDBKey} + {@code clear} try/finally
 * blocks. Nested calls save/restore the outer {@link DBContextHolder} keys so an inner
 * {@code executeOnShard} does not wipe the caller's route.</p>
 */
public final class DBRouterTemplate {

    private DBRouterTemplate() {
    }

    /** Route by userId (or other hash key), run action, then clear/restore. */
    public static <T> T executeOnShard(IDBRouterStrategy router, String routeKey, Supplier<T> action) {
        String prevDb = DBContextHolder.getDBKey();
        String prevTb = DBContextHolder.getTBKey();
        try {
            router.doRouter(routeKey);
            return action.get();
        } finally {
            restore(router, prevDb, prevTb);
        }
    }

    /** Route by userId, run void action, then clear/restore. */
    public static void executeOnShard(IDBRouterStrategy router, String routeKey, Runnable action) {
        executeOnShard(router, routeKey, () -> {
            action.run();
            return null;
        });
    }

    /** Pin to a concrete db index (jobs that scan db01/db02), then clear/restore. */
    public static <T> T executeOnDb(IDBRouterStrategy router, int dbIdx, Supplier<T> action) {
        String prevDb = DBContextHolder.getDBKey();
        String prevTb = DBContextHolder.getTBKey();
        try {
            router.setDBKey(dbIdx);
            return action.get();
        } finally {
            restore(router, prevDb, prevTb);
        }
    }

    /** Pin to a concrete db index, run void action, then clear/restore. */
    public static void executeOnDb(IDBRouterStrategy router, int dbIdx, Runnable action) {
        executeOnDb(router, dbIdx, () -> {
            action.run();
            return null;
        });
    }

    /** Pin to db + table index (order shard scans), then clear/restore. */
    public static <T> T executeOnDbTb(IDBRouterStrategy router, int dbIdx, int tbIdx, Supplier<T> action) {
        String prevDb = DBContextHolder.getDBKey();
        String prevTb = DBContextHolder.getTBKey();
        try {
            router.setDBKey(dbIdx);
            router.setTBKey(tbIdx);
            return action.get();
        } finally {
            restore(router, prevDb, prevTb);
        }
    }

    /** Pin to db + table index, run void action, then clear/restore. */
    public static void executeOnDbTb(IDBRouterStrategy router, int dbIdx, int tbIdx, Runnable action) {
        executeOnDbTb(router, dbIdx, tbIdx, () -> {
            action.run();
            return null;
        });
    }

    private static void restore(IDBRouterStrategy router, String prevDb, String prevTb) {
        router.clear();
        if (prevDb != null) {
            DBContextHolder.setDBKey(prevDb);
        }
        if (prevTb != null) {
            DBContextHolder.setTBKey(prevTb);
        }
    }
}

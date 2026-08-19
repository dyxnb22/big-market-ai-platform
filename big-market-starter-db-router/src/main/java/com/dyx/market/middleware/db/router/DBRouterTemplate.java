package com.dyx.market.middleware.db.router;

import com.dyx.market.middleware.db.router.strategy.IDBRouterStrategy;

import java.util.function.Supplier;

/**
 * ThreadLocal 分库分表路由辅助工具：设置路由、执行任务，并始终清理或恢复上下文。
 *
 * <p>调用方应优先使用本工具，避免手写 {@code doRouter}/{@code setDBKey} 与
 * {@code clear} 的 try/finally。嵌套调用会保存并恢复外层 {@link DBContextHolder} 键，
 * 因此内层 {@code executeOnShard} 不会清除调用方原有路由。</p>
 */
public final class DBRouterTemplate {

    private DBRouterTemplate() {
    }

    /** 按 userId 或其他哈希键路由，执行返回结果的任务后清理或恢复上下文。 */
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

    /** 按 userId 或其他哈希键路由，执行无返回值任务后清理或恢复上下文。 */
    public static void executeOnShard(IDBRouterStrategy router, String routeKey, Runnable action) {
        executeOnShard(router, routeKey, () -> {
            action.run();
            return null;
        });
    }

    /** 固定到指定数据库分片（例如扫描 db01/db02 的任务），执行后清理或恢复上下文。 */
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

    /** 固定到指定数据库分片，执行无返回值任务后清理或恢复上下文。 */
    public static void executeOnDb(IDBRouterStrategy router, int dbIdx, Runnable action) {
        executeOnDb(router, dbIdx, () -> {
            action.run();
            return null;
        });
    }

    /** 固定到指定数据库和表分片（例如订单分片扫描），执行后清理或恢复上下文。 */
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

    /** 固定到指定数据库和表分片，执行无返回值任务后清理或恢复上下文。 */
    public static void executeOnDbTb(IDBRouterStrategy router, int dbIdx, int tbIdx, Runnable action) {
        executeOnDbTb(router, dbIdx, tbIdx, () -> {
            action.run();
            return null;
        });
    }

    /** 清理当前路由，并在存在外层路由时恢复外层数据库和表键。 */
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

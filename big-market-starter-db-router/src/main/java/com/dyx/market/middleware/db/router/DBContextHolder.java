package com.dyx.market.middleware.db.router;

/**
 * Keeps the current routing decision for one request thread.
 *
 * <p>Always call {@link #clear()} in a finally block after a routed operation.
 * Thread pools reuse threads, so stale route values are dangerous.</p>
 */
public final class DBContextHolder {

    private static final ThreadLocal<String> DB_KEY = new ThreadLocal<>();
    private static final ThreadLocal<String> TB_KEY = new ThreadLocal<>();

    private DBContextHolder() {
    }

    public static void setDBKey(String dbKey) {
        DB_KEY.set(dbKey);
    }

    public static String getDBKey() {
        return DB_KEY.get();
    }

    public static void setTBKey(String tbKey) {
        TB_KEY.set(tbKey);
    }

    public static String getTBKey() {
        return TB_KEY.get();
    }

    public static void clear() {
        DB_KEY.remove();
        TB_KEY.remove();
    }

}

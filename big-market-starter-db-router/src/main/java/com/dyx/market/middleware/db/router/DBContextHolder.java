package com.dyx.market.middleware.db.router;

/**
 * 保存当前请求线程的数据库路由决策。
 *
 * <p>路由操作结束后务必在 finally 块中调用 {@link #clear()}。
 * 线程池会复用线程，残留的路由值会导致后续请求走错库表。</p>
 */
public final class DBContextHolder {

    private static final ThreadLocal<String> DB_KEY = new ThreadLocal<>();
    private static final ThreadLocal<String> TB_KEY = new ThreadLocal<>();

    private DBContextHolder() {
    }

    /** 设置当前线程的目标数据源键（如 db01）。 */
    public static void setDBKey(String dbKey) {
        DB_KEY.set(dbKey);
    }

    /** 获取当前线程的数据源键。 */
    public static String getDBKey() {
        return DB_KEY.get();
    }

    /** 设置当前线程的分表后缀（如 003）。 */
    public static void setTBKey(String tbKey) {
        TB_KEY.set(tbKey);
    }

    /** 获取当前线程的分表后缀。 */
    public static String getTBKey() {
        return TB_KEY.get();
    }

    /** 清除当前线程的路由上下文，防止线程复用时污染。 */
    public static void clear() {
        DB_KEY.remove();
        TB_KEY.remove();
    }

}

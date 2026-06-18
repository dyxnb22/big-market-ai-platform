package com.dyx.market.middleware.db.router.strategy;

/**
 * 分库分表路由策略接口。
 */
public interface IDBRouterStrategy {

    /**
     * 根据路由键计算并写入当前线程的库表路由。
     *
     * @param routeKey 路由键，通常为 userId
     */
    void doRouter(String routeKey);

    /**
     * 设置目标数据源索引。
     *
     * @param dbIdx 库序号，从 1 开始
     */
    void setDBKey(int dbIdx);

    /**
     * 设置目标分表索引。
     *
     * @param tbIdx 表序号，从 0 开始
     */
    void setTBKey(int tbIdx);

    /** 清除当前线程的路由上下文。 */
    void clear();

}

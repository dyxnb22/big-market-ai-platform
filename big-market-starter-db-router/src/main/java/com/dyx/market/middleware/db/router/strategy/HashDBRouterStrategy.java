package com.dyx.market.middleware.db.router.strategy;

import com.dyx.market.middleware.db.router.DBContextHolder;
import com.dyx.market.middleware.db.router.config.DBRouterProperties;

/**
 * 基于哈希取模的简单分库分表路由策略。
 *
 * <p>适用于平台学习版本的默认路由实现。</p>
 */
public class HashDBRouterStrategy implements IDBRouterStrategy {

    private final DBRouterProperties properties;

    public HashDBRouterStrategy(DBRouterProperties properties) {
        this.properties = properties;
    }

    @Override
    public void doRouter(String routeKey) {
        int hash = routeKey.hashCode() & Integer.MAX_VALUE;
        int dbIdx = hash % properties.getDbCount() + 1;
        int tbIdx = (hash / properties.getDbCount()) % properties.getTbCount();
        setDBKey(dbIdx);
        setTBKey(tbIdx);
    }

    @Override
    public void setDBKey(int dbIdx) {
        DBContextHolder.setDBKey(String.format("db%02d", dbIdx));
    }

    @Override
    public void setTBKey(int tbIdx) {
        DBContextHolder.setTBKey(String.format("%03d", tbIdx));
    }

    @Override
    public void clear() {
        DBContextHolder.clear();
    }

}

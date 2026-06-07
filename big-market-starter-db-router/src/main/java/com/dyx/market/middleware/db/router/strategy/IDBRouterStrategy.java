package com.dyx.market.middleware.db.router.strategy;

public interface IDBRouterStrategy {

    void doRouter(String routeKey);

    void setDBKey(int dbIdx);

    void setTBKey(int tbIdx);

    void clear();

}

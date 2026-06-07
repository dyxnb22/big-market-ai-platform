package com.dyx.market.middleware.db.router.strategy;

import com.dyx.market.middleware.db.router.DBContextHolder;
import com.dyx.market.middleware.db.router.config.DBRouterProperties;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class HashDBRouterStrategyTest {

    @Test
    void routeKeyShouldSetDbAndTableKey() {
        DBRouterProperties properties = new DBRouterProperties();
        properties.setDbCount(2);
        properties.setTbCount(4);
        HashDBRouterStrategy strategy = new HashDBRouterStrategy(properties);

        strategy.doRouter("xiaofuge");

        Assertions.assertNotNull(DBContextHolder.getDBKey());
        Assertions.assertNotNull(DBContextHolder.getTBKey());

        strategy.clear();
        Assertions.assertNull(DBContextHolder.getDBKey());
        Assertions.assertNull(DBContextHolder.getTBKey());
    }
}

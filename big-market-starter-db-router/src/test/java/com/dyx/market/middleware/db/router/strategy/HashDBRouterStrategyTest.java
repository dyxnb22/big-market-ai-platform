package com.dyx.market.middleware.db.router.strategy;

import com.dyx.market.middleware.db.router.DBContextHolder;
import com.dyx.market.middleware.db.router.config.DBRouterProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class HashDBRouterStrategyTest {

    @AfterEach
    void tearDown() {
        DBContextHolder.clear();
    }

    @Test
    void routeKeyShouldSetDbAndTableKey() {
        HashDBRouterStrategy strategy = newStrategy(2, 4);

        strategy.doRouter("xiaofuge");

        Assertions.assertNotNull(DBContextHolder.getDBKey());
        Assertions.assertNotNull(DBContextHolder.getTBKey());
    }

    @Test
    void routeKeyWithMinIntegerHashShouldNotProduceNegativeIndex() {
        HashDBRouterStrategy strategy = newStrategy(4, 4);

        strategy.doRouter("polygenelubricants");

        Assertions.assertEquals(Integer.MIN_VALUE, "polygenelubricants".hashCode());
        Assertions.assertEquals("db01", DBContextHolder.getDBKey());
        Assertions.assertEquals("000", DBContextHolder.getTBKey());
    }

    @Test
    void routeKeyShouldBeDeterministic() {
        HashDBRouterStrategy strategy = newStrategy(2, 4);

        strategy.doRouter("user123");
        String db1 = DBContextHolder.getDBKey();
        String tb1 = DBContextHolder.getTBKey();
        strategy.clear();

        strategy.doRouter("user123");

        Assertions.assertEquals(db1, DBContextHolder.getDBKey());
        Assertions.assertEquals(tb1, DBContextHolder.getTBKey());
    }

    @Test
    void differentKeysShouldRouteToValidShards() {
        HashDBRouterStrategy strategy = newStrategy(4, 4);

        strategy.doRouter("alpha");
        Assertions.assertTrue(DBContextHolder.getDBKey().matches("db0[1-4]"));
        Assertions.assertTrue(DBContextHolder.getTBKey().matches("00[0-3]"));
        strategy.clear();

        strategy.doRouter("beta");
        Assertions.assertTrue(DBContextHolder.getDBKey().matches("db0[1-4]"));
        Assertions.assertTrue(DBContextHolder.getTBKey().matches("00[0-3]"));
    }

    private HashDBRouterStrategy newStrategy(int dbCount, int tbCount) {
        DBRouterProperties properties = new DBRouterProperties();
        properties.setDbCount(dbCount);
        properties.setTbCount(tbCount);
        return new HashDBRouterStrategy(properties);
    }
}

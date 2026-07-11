package com.dyx.market.middleware.db.router;

import com.dyx.market.middleware.db.router.config.DBRouterProperties;
import com.dyx.market.middleware.db.router.strategy.HashDBRouterStrategy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class DBRouterTemplateTest {

    @AfterEach
    void tearDown() {
        DBContextHolder.clear();
    }

    @Test
    void executeOnShard_setsRouteAndClears() {
        HashDBRouterStrategy strategy = newStrategy(2, 4);

        String seen = DBRouterTemplate.executeOnShard(strategy, "user123", () -> DBContextHolder.getDBKey());

        Assertions.assertNotNull(seen);
        Assertions.assertNull(DBContextHolder.getDBKey());
        Assertions.assertNull(DBContextHolder.getTBKey());
    }

    @Test
    void executeOnShard_nested_restoresOuterRoute() {
        HashDBRouterStrategy strategy = newStrategy(2, 4);
        strategy.doRouter("outer-user");
        String outerDb = DBContextHolder.getDBKey();
        String outerTb = DBContextHolder.getTBKey();

        DBRouterTemplate.executeOnShard(strategy, "inner-user", () -> {
            Assertions.assertNotNull(DBContextHolder.getDBKey());
            return null;
        });

        Assertions.assertEquals(outerDb, DBContextHolder.getDBKey());
        Assertions.assertEquals(outerTb, DBContextHolder.getTBKey());
    }

    @Test
    void executeOnDb_pinsIndexAndClears() {
        HashDBRouterStrategy strategy = newStrategy(2, 4);

        String seen = DBRouterTemplate.executeOnDb(strategy, 2, () -> DBContextHolder.getDBKey());

        Assertions.assertEquals("db02", seen);
        Assertions.assertNull(DBContextHolder.getDBKey());
    }

    @Test
    void executeOnDbTb_pinsDbAndTableThenClears() {
        HashDBRouterStrategy strategy = newStrategy(2, 4);

        String[] seen = DBRouterTemplate.executeOnDbTb(strategy, 1, 2, () ->
                new String[]{DBContextHolder.getDBKey(), DBContextHolder.getTBKey()});

        Assertions.assertEquals("db01", seen[0]);
        Assertions.assertEquals("002", seen[1]);
        Assertions.assertNull(DBContextHolder.getDBKey());
        Assertions.assertNull(DBContextHolder.getTBKey());
    }

    private HashDBRouterStrategy newStrategy(int dbCount, int tbCount) {
        DBRouterProperties properties = new DBRouterProperties();
        properties.setDbCount(dbCount);
        properties.setTbCount(tbCount);
        return new HashDBRouterStrategy(properties);
    }
}

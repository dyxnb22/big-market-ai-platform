package com.dyx.market.market;

import com.dyx.market.trigger.application.RaffleActivityFacade;
import com.dyx.market.trigger.support.AuthenticatedUserSupport;
import org.junit.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * BM-001：market-service 启动类必须声明 trigger.application/support/adapter 的扫描范围。
 */
public class MarketServiceApplicationContextTest {

    @Test
    public void scanBasePackagesIncludeApplicationLayer() {
        SpringBootApplication annotation = MarketServiceApplication.class.getAnnotation(SpringBootApplication.class);
        assertNotNull(annotation);
        Set<String> packages = new HashSet<>(Arrays.asList(annotation.scanBasePackages()));
        assertTrue(packages.contains("com.dyx.market.trigger.application"));
        assertTrue(packages.contains("com.dyx.market.trigger.support"));
        assertTrue(packages.contains("com.dyx.market.trigger.adapter"));
        assertTrue(!packages.contains("com.dyx.market.trigger.job"));
        assertTrue(!packages.contains("com.dyx.market.trigger.listener"));
    }

    @Test
    public void applicationLayerTypesAreLoadable() throws Exception {
        assertNotNull(Class.forName(RaffleActivityFacade.class.getName()));
        assertNotNull(Class.forName(AuthenticatedUserSupport.class.getName()));
    }
}

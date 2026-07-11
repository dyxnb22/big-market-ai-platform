package com.dyx.market.strategy;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.junit.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** GOV-B09: strategy-service launcher scan + Dubbo provider class gate. */
public class StrategyServiceApplicationScanTest {

    @Test
    public void applicationScansStrategyDomainInfrastructureAndEnablesDubbo() throws Exception {
        SpringBootApplication boot = StrategyServiceApplication.class.getAnnotation(SpringBootApplication.class);
        assertNotNull(boot);
        Set<String> packages = new HashSet<>(Arrays.asList(boot.scanBasePackages()));
        assertTrue(packages.contains("com.dyx.market.strategy"));
        assertTrue(packages.contains("com.dyx.market.domain.strategy"));
        assertTrue(packages.contains("com.dyx.market.infrastructure"));
        assertNotNull(StrategyServiceApplication.class.getAnnotation(EnableDubbo.class));
        assertNotNull(Class.forName("com.dyx.market.strategy.provider.StrategyReadServiceRPC"));
    }
}

package com.dyx.market.rebate;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.junit.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** GOV-B09: rebate-service launcher scan + Dubbo provider class gate. */
public class RebateServiceApplicationScanTest {

    @Test
    public void applicationScansRebateDomainInfrastructureAndEnablesDubbo() throws Exception {
        SpringBootApplication boot = RebateServiceApplication.class.getAnnotation(SpringBootApplication.class);
        assertNotNull(boot);
        Set<String> packages = new HashSet<>(Arrays.asList(boot.scanBasePackages()));
        assertTrue(packages.contains("com.dyx.market.rebate"));
        assertTrue(packages.contains("com.dyx.market.domain.rebate"));
        assertTrue(packages.contains("com.dyx.market.infrastructure"));
        assertNotNull(RebateServiceApplication.class.getAnnotation(EnableDubbo.class));
        assertNotNull(Class.forName("com.dyx.market.rebate.provider.RebateServiceRPC"));
    }
}

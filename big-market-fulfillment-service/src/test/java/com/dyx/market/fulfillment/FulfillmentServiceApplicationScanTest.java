package com.dyx.market.fulfillment;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.junit.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** GOV-B09: fulfillment-service launcher scan + Dubbo provider class gate. */
public class FulfillmentServiceApplicationScanTest {

    @Test
    public void applicationScansFulfillmentAwardInfrastructureAndEnablesDubbo() throws Exception {
        SpringBootApplication boot = FulfillmentServiceApplication.class.getAnnotation(SpringBootApplication.class);
        assertNotNull(boot);
        Set<String> packages = new HashSet<>(Arrays.asList(boot.scanBasePackages()));
        assertTrue(packages.contains("com.dyx.market.fulfillment"));
        assertTrue(packages.contains("com.dyx.market.domain.award"));
        assertTrue(packages.contains("com.dyx.market.infrastructure"));
        assertNotNull(FulfillmentServiceApplication.class.getAnnotation(EnableDubbo.class));
        assertNotNull(Class.forName("com.dyx.market.fulfillment.provider.FulfillmentAwardServiceRPC"));
    }
}

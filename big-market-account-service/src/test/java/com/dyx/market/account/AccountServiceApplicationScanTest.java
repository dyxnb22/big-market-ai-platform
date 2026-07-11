package com.dyx.market.account;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.junit.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * GOV-B09: account-service launcher scan + Dubbo enablement gate (no full Context).
 */
public class AccountServiceApplicationScanTest {

    @Test
    public void applicationScansAccountDomainInfrastructureAndEnablesDubbo() throws Exception {
        SpringBootApplication boot = AccountServiceApplication.class.getAnnotation(SpringBootApplication.class);
        assertNotNull(boot);
        Set<String> packages = new HashSet<>(Arrays.asList(boot.scanBasePackages()));
        assertTrue(packages.contains("com.dyx.market.account"));
        assertTrue(packages.contains("com.dyx.market.domain"));
        assertTrue(packages.contains("com.dyx.market.infrastructure"));
        assertNotNull(AccountServiceApplication.class.getAnnotation(EnableDubbo.class));
        assertNotNull(Class.forName("com.dyx.market.account.provider.AccountCreditServiceRPC"));
        assertNotNull(Class.forName("com.dyx.market.account.provider.AccountQuotaServiceRPC"));
    }
}

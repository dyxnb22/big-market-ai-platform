package com.dyx.market.account;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.junit.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * GOV-B09：校验 account-service 启动类扫描范围与 Dubbo 启用配置（不加载完整上下文）。
 */
public class AccountServiceApplicationScanTest {

    @Test
    public void applicationScansAccountAndExplicitlyExcludesMarketOnlyDrawComposition() throws Exception {
        assertNotNull(AccountServiceApplication.class.getAnnotation(SpringBootConfiguration.class));
        assertNotNull(AccountServiceApplication.class.getAnnotation(EnableAutoConfiguration.class));
        ComponentScan scan = AccountServiceApplication.class.getAnnotation(ComponentScan.class);
        assertNotNull(scan);
        Set<String> sharedPackages = new HashSet<>(Arrays.asList(scan.basePackages()));
        assertTrue(sharedPackages.contains("com.dyx.market.account"));
        assertTrue(sharedPackages.contains("com.dyx.market.domain"));
        assertTrue(sharedPackages.contains("com.dyx.market.infrastructure"));
        assertTrue(sharedPackages.contains("com.dyx.market.trigger.account"));
        assertTrue(scan.excludeFilters().length > 0);
        assertNotNull(AccountServiceApplication.class.getAnnotation(EnableDubbo.class));
        assertNotNull(Class.forName("com.dyx.market.account.provider.AccountCreditServiceRPC"));
        assertNotNull(Class.forName("com.dyx.market.account.provider.AccountQuotaServiceRPC"));
        assertNotNull(Class.forName("com.dyx.market.trigger.account.RemoteActivityAccountPort"));
    }
}

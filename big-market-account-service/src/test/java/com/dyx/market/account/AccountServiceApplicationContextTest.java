package com.dyx.market.account;

import com.dyx.market.account.application.AccountCreditApplicationService;
import com.dyx.market.account.provider.AccountCreditServiceRPC;
import com.dyx.market.account.provider.AccountQuotaServiceRPC;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import static org.junit.Assert.assertNotNull;

/**
 * Account-service full Application Context load (upgrade gate before Boot 3).
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = AccountServiceApplication.class, properties = {
        "spring.main.lazy-initialization=false",
        "spring.cloud.client.hostname=localhost",
        "spring.cloud.client.ip-address=127.0.0.1",
        "default-credential-guard.enabled=false"
})
@ActiveProfiles("test")
public class AccountServiceApplicationContextTest {

    static {
        System.setProperty("spring.cloud.inetutils.preferred-networks", "127.0.0.1");
        System.setProperty("spring.cloud.client.hostname", "localhost");
        System.setProperty("spring.cloud.client.ip-address", "127.0.0.1");
        java.io.File dubboCache = new java.io.File("target/dubbo-cache");
        dubboCache.mkdirs();
        System.setProperty("dubbo.meta.cache.filePath", new java.io.File(dubboCache, "meta").getAbsolutePath());
        System.setProperty("dubbo.mapping.cache.filePath", new java.io.File(dubboCache, "mapping").getAbsolutePath());
    }

    @MockBean
    private RedissonClient redissonClient;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private AccountCreditApplicationService accountCreditApplicationService;

    @Autowired
    private AccountCreditServiceRPC accountCreditServiceRPC;

    @Autowired
    private AccountQuotaServiceRPC accountQuotaServiceRPC;

    @Test
    public void contextLoads_requiredBeansPresent() {
        assertNotNull(applicationContext);
        assertNotNull(accountCreditApplicationService);
        assertNotNull(accountCreditServiceRPC);
        assertNotNull(accountQuotaServiceRPC);
    }
}

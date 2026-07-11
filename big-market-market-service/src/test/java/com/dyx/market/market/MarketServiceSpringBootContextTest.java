package com.dyx.market.market;

import com.dyx.market.domain.chat.adapter.repository.IChatCreditSessionRepository;
import com.dyx.market.trigger.application.ChatCreditApplicationService;
import com.dyx.market.trigger.application.RaffleActivityFacade;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import static org.junit.Assert.assertNotNull;

/**
 * BM-001: market-service full Application Context loads with trigger.application beans.
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = MarketServiceApplication.class, properties = {
        "spring.cloud.client.hostname=localhost",
        "spring.cloud.client.ip-address=127.0.0.1",
        "spring.main.cloud-platform=none"
})
@ActiveProfiles("test")
public class MarketServiceSpringBootContextTest {

    static {
        // Avoid HostInfoEnvironmentPostProcessor calling InetAddress.getLocalHost() in restricted CI sandboxes.
        System.setProperty("spring.cloud.inetutils.preferred-networks", "127.0.0.1");
        System.setProperty("spring.cloud.client.hostname", "localhost");
        System.setProperty("spring.cloud.client.ip-address", "127.0.0.1");
        // Redirect Dubbo local file cache away from ~/.dubbo without changing user.home globally
        // (changing user.home breaks Dubbo ApplicationConfig instantiation in some environments).
        java.io.File dubboCache = new java.io.File("target/dubbo-cache");
        dubboCache.mkdirs();
        System.setProperty("dubbo.meta.cache.filePath", new java.io.File(dubboCache, "meta").getAbsolutePath());
        System.setProperty("dubbo.mapping.cache.filePath", new java.io.File(dubboCache, "mapping").getAbsolutePath());
    }

    @MockBean
    private RedissonClient redissonClient;

    @Autowired
    private RaffleActivityFacade raffleActivityFacade;

    @Autowired
    private ChatCreditApplicationService chatCreditApplicationService;

    @Autowired
    private IChatCreditSessionRepository chatCreditSessionRepository;

    @Test
    public void contextLoads_requiredApplicationBeansPresent() {
        assertNotNull(raffleActivityFacade);
        assertNotNull(chatCreditApplicationService);
        assertNotNull(chatCreditSessionRepository);
    }
}

package com.dyx.market.market;

import com.dyx.market.domain.chat.adapter.repository.IChatCreditSessionRepository;
import com.dyx.market.trigger.application.ChatCreditApplicationService;
import com.dyx.market.trigger.application.RaffleActivityFacade;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import static org.junit.Assert.assertNotNull;

/**
 * BM-001：market-service 完整 Spring 上下文必须加载 trigger.application Bean。
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
        // 避免 HostInfoEnvironmentPostProcessor 在受限 CI 沙箱中调用 InetAddress.getLocalHost()。
        System.setProperty("spring.cloud.inetutils.preferred-networks", "127.0.0.1");
        System.setProperty("spring.cloud.client.hostname", "localhost");
        System.setProperty("spring.cloud.client.ip-address", "127.0.0.1");
        // 将 Dubbo 本地文件缓存重定向到 ~/.dubbo 之外，不全局修改 user.home
        //（部分环境中修改 user.home 会破坏 Dubbo ApplicationConfig 实例化）。
        java.io.File dubboCache = new java.io.File("target/dubbo-cache");
        dubboCache.mkdirs();
        System.setProperty("dubbo.registry.file", new java.io.File(dubboCache, "registry.properties").getAbsolutePath());
        System.setProperty("dubbo.meta.cache.filePath", new java.io.File(dubboCache, "meta").getAbsolutePath());
        System.setProperty("dubbo.mapping.cache.filePath", new java.io.File(dubboCache, "mapping").getAbsolutePath());
    }

    @MockitoBean
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

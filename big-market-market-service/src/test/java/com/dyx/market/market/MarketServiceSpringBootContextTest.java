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
@SpringBootTest(classes = MarketServiceApplication.class)
@ActiveProfiles("test")
public class MarketServiceSpringBootContextTest {

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

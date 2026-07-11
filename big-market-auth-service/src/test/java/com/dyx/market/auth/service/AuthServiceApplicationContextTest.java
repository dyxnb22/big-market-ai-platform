package com.dyx.market.auth.service;

import com.dyx.market.auth.AuthAccessController;
import com.dyx.market.domain.auth.service.IAuthService;
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
 * GOV-B09: auth-service Context load without lazy-init (eager bean wiring gate).
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = AuthServiceApplication.class, properties = {
        "spring.main.lazy-initialization=false",
        "spring.cloud.client.hostname=localhost",
        "spring.cloud.client.ip-address=127.0.0.1",
        "default-credential-guard.enabled=false"
})
@ActiveProfiles("test")
public class AuthServiceApplicationContextTest {

    @MockBean
    private RedissonClient redissonClient;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private IAuthService authService;

    @Autowired
    private AuthAccessController authAccessController;

    @Test
    public void contextLoads_requiredBeansPresent() {
        assertNotNull(applicationContext);
        assertNotNull(authService);
        assertNotNull(authAccessController);
    }
}

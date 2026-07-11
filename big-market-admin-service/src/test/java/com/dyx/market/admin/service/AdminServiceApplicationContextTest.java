package com.dyx.market.admin.service;

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
 * GOV-B09: admin-service minimal Context load.
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = AdminServiceApplication.class, properties = {
        "spring.main.lazy-initialization=false",
        "spring.cloud.client.hostname=localhost",
        "nacos.config.sync.enabled=false",
        "default-credential-guard.enabled=false"
})
@ActiveProfiles("test")
public class AdminServiceApplicationContextTest {

    @MockBean
    private RedissonClient redissonClient;

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    public void contextLoads() {
        assertNotNull(applicationContext);
    }
}

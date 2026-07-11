package com.dyx.market.management.config;

import com.dyx.market.trigger.api.dto.AdminConfigRequestDTO;
import com.dyx.market.trigger.api.dto.AdminConfigResponseDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.io.IOException;

class PlatformConfigServiceTest {

    private static final File TEST_STORE = new File("target/platform-config-test.properties");

    @AfterEach
    void clean() {
        System.clearProperty("big.market.config.store");
        if (TEST_STORE.exists()) {
            TEST_STORE.delete();
        }
    }

    @Test
    void saveAndReloadConfig() throws Exception {
        System.setProperty("big.market.config.store", TEST_STORE.getPath());
        PlatformConfigService service = new PlatformConfigService();
        service.afterPropertiesSet();

        AdminConfigRequestDTO request = new AdminConfigRequestDTO();
        request.setNamespace("chatbot");
        request.setConfigKey("enabled");
        request.setConfigValue("false");
        request.setDescription("test switch");
        service.save(request);

        PlatformConfigService reloadService = new PlatformConfigService();
        reloadService.afterPropertiesSet();
        AdminConfigResponseDTO config = reloadService.get("chatbot", "enabled");

        Assertions.assertNotNull(config);
        Assertions.assertEquals("false", config.getConfigValue());
        Assertions.assertEquals("test switch", config.getDescription());
    }

    @Test
    void save_should_rollback_config_on_dcc_sync_failure() throws Exception {
        System.setProperty("big.market.config.store", TEST_STORE.getPath());
        PlatformConfigService service = new PlatformConfigService();
        ReflectionTestUtils.setField(service, "dynamicConfigSyncPort", new DynamicConfigSyncPort() {
            @Override
            public void syncDegradeSwitch(String value) {
                throw new RuntimeException("dcc sync failed");
            }

            @Override
            public void syncRateLimiterSwitch(String value) {
                throw new RuntimeException("dcc sync failed");
            }
        });
        service.afterPropertiesSet();

        AdminConfigRequestDTO request = new AdminConfigRequestDTO();
        request.setNamespace("system");
        request.setConfigKey("rateLimiterSwitch");
        request.setConfigValue("open");
        request.setDescription("rate limiter");

        RuntimeException ex = Assertions.assertThrows(RuntimeException.class, () -> service.save(request));
        Assertions.assertEquals("dcc sync failed", ex.getMessage());
        Assertions.assertEquals("close", service.get("system", "rateLimiterSwitch").getConfigValue());

        PlatformConfigService reloadService = new PlatformConfigService();
        reloadService.afterPropertiesSet();
        Assertions.assertEquals("close", reloadService.get("system", "rateLimiterSwitch").getConfigValue());
    }

    @Test
    void save_should_fail_when_nacos_publish_fails_closed() throws Exception {
        System.setProperty("big.market.config.store", TEST_STORE.getPath());
        PlatformConfigService service = new PlatformConfigService();
        NacosConfigSyncService nacos = org.mockito.Mockito.mock(NacosConfigSyncService.class);
        org.mockito.Mockito.doThrow(new IllegalStateException("Nacos publishConfig returned false"))
                .when(nacos).publish(org.mockito.ArgumentMatchers.anyString());
        ReflectionTestUtils.setField(service, "nacosConfigSyncService", nacos);
        service.afterPropertiesSet();

        AdminConfigRequestDTO request = new AdminConfigRequestDTO();
        request.setNamespace("chatbot");
        request.setConfigKey("enabled");
        request.setConfigValue("false");
        request.setDescription("nacos fail");

        IOException ex = Assertions.assertThrows(IOException.class, () -> service.save(request));
        Assertions.assertTrue(ex.getMessage().contains("Nacos"));
        Assertions.assertEquals("true", service.get("chatbot", "enabled").getConfigValue());
    }

    @Test
    void save_should_attach_content_hash_and_nacos_metadata() throws Exception {
        System.setProperty("big.market.config.store", TEST_STORE.getPath());
        PlatformConfigService service = new PlatformConfigService();
        NacosConfigSyncService nacos = org.mockito.Mockito.mock(NacosConfigSyncService.class);
        org.mockito.Mockito.when(nacos.publish(org.mockito.ArgumentMatchers.anyString())).thenReturn(true);
        ReflectionTestUtils.setField(service, "nacosConfigSyncService", nacos);
        service.afterPropertiesSet();

        AdminConfigRequestDTO request = new AdminConfigRequestDTO();
        request.setNamespace("chatbot");
        request.setConfigKey("enabled");
        request.setConfigValue("false");
        request.setDescription("hash test");

        AdminConfigResponseDTO saved = service.save(request);
        Assertions.assertNotNull(saved.getContentHash());
        Assertions.assertEquals(16, saved.getContentHash().length());
        Assertions.assertTrue(saved.getNacosPublished());
        Assertions.assertEquals("nacos", saved.getSource());
    }
}

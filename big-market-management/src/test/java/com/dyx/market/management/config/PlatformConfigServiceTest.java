package com.dyx.market.management.config;

import com.dyx.market.trigger.api.dto.AdminConfigRequestDTO;
import com.dyx.market.trigger.api.dto.AdminConfigResponseDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.File;

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
}

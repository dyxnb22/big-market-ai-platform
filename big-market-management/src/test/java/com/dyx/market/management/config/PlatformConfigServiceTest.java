package com.dyx.market.management.config;

import com.dyx.market.trigger.api.dto.AdminConfigRequestDTO;
import com.dyx.market.trigger.api.dto.AdminConfigResponseDTO;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;

class PlatformConfigServiceTest {

    @Test
    void savePublishesToNacosWithoutLocalPersistence() throws Exception {
        PlatformConfigService service = new PlatformConfigService();
        NacosConfigSyncService nacos = org.mockito.Mockito.mock(NacosConfigSyncService.class);
        org.mockito.Mockito.when(nacos.publish(org.mockito.ArgumentMatchers.anyString())).thenReturn(true);
        ReflectionTestUtils.setField(service, "nacosConfigSyncService", nacos);
        service.afterPropertiesSet();

        AdminConfigRequestDTO request = new AdminConfigRequestDTO();
        request.setNamespace("chatbot");
        request.setConfigKey("enabled");
        request.setConfigValue("false");
        request.setDescription("test switch");
        AdminConfigResponseDTO config = service.save(request);

        Assertions.assertNotNull(config);
        Assertions.assertEquals("false", config.getConfigValue());
        Assertions.assertEquals("test switch", config.getDescription());
        Assertions.assertEquals("nacos", config.getSource());
        org.mockito.Mockito.verify(nacos).publish(org.mockito.ArgumentMatchers.argThat(content ->
                content.contains("chatbot.enabled.value=false")));
    }

    @Test
    void save_should_publish_runtime_switches_to_nacos() throws Exception {
        PlatformConfigService service = new PlatformConfigService();
        NacosConfigSyncService nacos = org.mockito.Mockito.mock(NacosConfigSyncService.class);
        org.mockito.Mockito.when(nacos.publishRuntimeSwitches(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(true);
        ReflectionTestUtils.setField(service, "nacosConfigSyncService", nacos);
        service.afterPropertiesSet();

        AdminConfigRequestDTO request = new AdminConfigRequestDTO();
        request.setNamespace("system");
        request.setConfigKey("rateLimiterSwitch");
        request.setConfigValue("open");
        request.setDescription("rate limiter");

        AdminConfigResponseDTO saved = service.save(request);
        Assertions.assertTrue(saved.getNacosPublished());
        org.mockito.Mockito.verify(nacos).publishRuntimeSwitches(
                org.mockito.ArgumentMatchers.argThat(content -> content.contains("system.rateLimiterSwitch.value=open")
                        && !content.contains("chatbot.apiKey")));
    }

    @Test
    void save_should_fail_when_nacos_publish_fails_closed() throws Exception {
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

    @Test
    void refreshReplacesTheEntireScopeSoDeletedValuesCannotRemainActive() throws Exception {
        PlatformConfigService service = new PlatformConfigService();
        service.afterPropertiesSet();

        service.refreshPlatformFromContent("chatbot.enabled.value=false\n");
        Assertions.assertEquals("false", service.get("chatbot", "enabled").getConfigValue());

        service.refreshPlatformFromContent("chatbot.provider.value=local\n");
        Assertions.assertEquals("true", service.get("chatbot", "enabled").getConfigValue());
        Assertions.assertEquals("local", service.get("chatbot", "provider").getConfigValue());
    }
}

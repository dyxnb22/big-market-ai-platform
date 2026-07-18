package com.dyx.market.management.config;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.sql.Connection;
import java.sql.PreparedStatement;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.verify;

class NacosConfigSyncServiceTest {

    @Test
    void sameConfigValues_ignoresPropertiesStoreTimestampComments() {
        String expected = "#Big Market runtime switches\n"
                + "#Sat Jul 18 21:00:00 CST 2026\n"
                + "system.degradeSwitch.value=open\n"
                + "system.rateLimiterSwitch.value=close\n";
        String actual = "#Big Market runtime switches\n"
                + "#Sat Jul 18 22:00:00 CST 2026\n"
                + "system.degradeSwitch.value=open\n"
                + "system.rateLimiterSwitch.value=close\n";
        Assertions.assertTrue(NacosConfigSyncService.sameConfigValues(expected, actual));
    }

    @Test
    void sameConfigValues_detectsValueDrift() {
        Assertions.assertFalse(NacosConfigSyncService.sameConfigValues(
                "system.degradeSwitch.value=open\n",
                "system.degradeSwitch.value=close\n"));
    }

    @Test
    void supportedNamespace_rejectsCustomNamespaceBecauseTwinSyncUsesPublicStorage() {
        Assertions.assertTrue(NacosConfigSyncService.isSupportedNamespace("public"));
        Assertions.assertTrue(NacosConfigSyncService.isSupportedNamespace(""));
        Assertions.assertFalse(NacosConfigSyncService.isSupportedNamespace("tenant-a"));
    }

    @Test
    void upsertPublicTwin_usesSingleAtomicDuplicateKeyStatement() throws Exception {
        Connection connection = Mockito.mock(Connection.class);
        PreparedStatement statement = Mockito.mock(PreparedStatement.class);
        Mockito.when(connection.prepareStatement(anyString())).thenReturn(statement);

        NacosConfigSyncService service = new NacosConfigSyncService();
        service.upsertPublicTwin(connection, "data-id", "DEFAULT_GROUP", "key=value\n");

        verify(connection).prepareStatement(contains("ON DUPLICATE KEY UPDATE"));
        verify(statement).executeUpdate();
    }
}

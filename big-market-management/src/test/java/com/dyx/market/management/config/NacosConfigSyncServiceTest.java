package com.dyx.market.management.config;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

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
}

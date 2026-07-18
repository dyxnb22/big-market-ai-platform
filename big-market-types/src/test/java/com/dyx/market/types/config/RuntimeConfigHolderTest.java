package com.dyx.market.types.config;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class RuntimeConfigHolderTest {

    @Test
    void refreshReplacesBothSwitchesAsOneSnapshot() {
        RuntimeConfigHolder holder = new RuntimeConfigHolder();

        holder.refreshFromContent("system.degradeSwitch.value=open\n"
                + "system.rateLimiterSwitch.value=open\n");

        Assertions.assertTrue(holder.isDegradeOpen());
        Assertions.assertTrue(holder.isRateLimiterEnabled());
        Assertions.assertEquals("open", holder.snapshot().getDegradeSwitch());
        Assertions.assertEquals("open", holder.snapshot().getRateLimiterSwitch());
    }

    @Test
    void missingValuesResetTheAffectedSnapshotToSafeDefaults() {
        RuntimeConfigHolder holder = new RuntimeConfigHolder();
        holder.refreshFromContent("system.degradeSwitch.value=open\n"
                + "system.rateLimiterSwitch.value=open\n");

        holder.refreshFromContent("system.degradeSwitch.description=unchanged\n");

        Assertions.assertFalse(holder.isDegradeOpen());
        Assertions.assertFalse(holder.isRateLimiterEnabled());
    }

    @Test
    void deletedSwitchReturnsToSafeDefault() {
        RuntimeConfigHolder holder = new RuntimeConfigHolder();
        holder.refreshFromContent("system.degradeSwitch.value=open\n");

        holder.refreshFromContent("system.degradeSwitch.value=\n"
                + "system.degradeSwitch.description=__DELETED__\n");

        Assertions.assertFalse(holder.isDegradeOpen());
    }
}

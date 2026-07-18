package com.dyx.market.types.config;

import org.apache.commons.lang3.StringUtils;

import java.io.StringReader;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Immutable runtime-switch snapshot shared by the market request path.
 *
 * <p>The snapshot is replaced as a whole when Nacos publishes a new runtime
 * configuration, so a request never observes a partially refreshed pair of
 * switches.</p>
 */
public final class RuntimeConfigHolder {

    public static final String DEGRADE_SWITCH = "system.degradeSwitch";
    public static final String RATE_LIMITER_SWITCH = "system.rateLimiterSwitch";

    private final AtomicReference<RuntimeConfigSnapshot> snapshot = new AtomicReference<>(
            new RuntimeConfigSnapshot("close", "close"));

    public void refreshFromContent(String content) {
        try {
            Properties properties = new Properties();
            if (StringUtils.isNotBlank(content)) {
                properties.load(new StringReader(content));
            }
            // Each Nacos notification is a full DataId snapshot. Missing, deleted,
            // blank, and invalid values must reset safely rather than retain a stale
            // previous switch.
            String degradeSwitch = valueOf(properties, DEGRADE_SWITCH);
            String rateLimiterSwitch = valueOf(properties, RATE_LIMITER_SWITCH);
            snapshot.set(new RuntimeConfigSnapshot(degradeSwitch, rateLimiterSwitch));
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse runtime config content", e);
        }
    }

    public String getDegradeSwitch() {
        return snapshot.get().getDegradeSwitch();
    }

    public String getRateLimiterSwitch() {
        return snapshot.get().getRateLimiterSwitch();
    }

    public boolean isDegradeOpen() {
        return "open".equalsIgnoreCase(getDegradeSwitch());
    }

    public boolean isRateLimiterEnabled() {
        String value = getRateLimiterSwitch();
        return StringUtils.isNotBlank(value) && !"close".equalsIgnoreCase(value);
    }

    public RuntimeConfigSnapshot snapshot() {
        return snapshot.get();
    }

    private String valueOf(Properties properties, String key) {
        if ("__DELETED__".equals(properties.getProperty(key + ".description"))) {
            return "close";
        }
        String value = properties.getProperty(key + ".value");
        return "open".equalsIgnoreCase(value) ? "open" : "close";
    }

    public static final class RuntimeConfigSnapshot {
        private final String degradeSwitch;
        private final String rateLimiterSwitch;

        private RuntimeConfigSnapshot(String degradeSwitch, String rateLimiterSwitch) {
            this.degradeSwitch = degradeSwitch;
            this.rateLimiterSwitch = rateLimiterSwitch;
        }

        public String getDegradeSwitch() {
            return degradeSwitch;
        }

        public String getRateLimiterSwitch() {
            return rateLimiterSwitch;
        }
    }
}

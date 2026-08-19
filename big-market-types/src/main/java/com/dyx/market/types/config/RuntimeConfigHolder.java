package com.dyx.market.types.config;

import org.apache.commons.lang3.StringUtils;

import java.io.StringReader;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 供 market 请求链路共享的不可变运行时开关快照。
 *
 * <p>Nacos 发布新的运行时配置时整体替换快照，因此单个请求不会读到两个开关
 * 分别来自不同配置代的中间状态。</p>
 */
public final class RuntimeConfigHolder {

    public static final String DEGRADE_SWITCH = "system.degradeSwitch";
    public static final String RATE_LIMITER_SWITCH = "system.rateLimiterSwitch";

    private final AtomicReference<RuntimeConfigSnapshot> snapshot = new AtomicReference<>(
            new RuntimeConfigSnapshot("close", "close"));

    /**
     * 从完整的 Nacos DataId 内容刷新快照。
     *
     * <p>缺失、删除、空值和非法值都会安全恢复为关闭状态，不沿用上一代开关。</p>
     *
     * @param content Nacos properties 格式的完整配置内容
     */
    public void refreshFromContent(String content) {
        try {
            Properties properties = new Properties();
            if (StringUtils.isNotBlank(content)) {
                properties.load(new StringReader(content));
            }
            // 每次 Nacos 通知都是完整 DataId 快照；缺失、删除、空值和非法值必须安全重置，
            // 不能继续保留上一代开关。
            String degradeSwitch = valueOf(properties, DEGRADE_SWITCH);
            String rateLimiterSwitch = valueOf(properties, RATE_LIMITER_SWITCH);
            snapshot.set(new RuntimeConfigSnapshot(degradeSwitch, rateLimiterSwitch));
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse runtime config content", e);
        }
    }

    /** 返回当前全局抽奖降级开关的原始值。 */
    public String getDegradeSwitch() {
        return snapshot.get().getDegradeSwitch();
    }

    /** 返回当前全局限流开关的原始值。 */
    public String getRateLimiterSwitch() {
        return snapshot.get().getRateLimiterSwitch();
    }

    /** 判断抽奖降级开关是否明确为 open。 */
    public boolean isDegradeOpen() {
        return "open".equalsIgnoreCase(getDegradeSwitch());
    }

    /** 判断限流开关是否启用；除 close 外的非空值均视为启用。 */
    public boolean isRateLimiterEnabled() {
        String value = getRateLimiterSwitch();
        return StringUtils.isNotBlank(value) && !"close".equalsIgnoreCase(value);
    }

    /** 返回当前不可变快照，供需要同时读取多个开关的调用方使用。 */
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
        /** 抽奖降级开关值。 */
        private final String degradeSwitch;
        /** 全局限流开关值。 */
        private final String rateLimiterSwitch;

        private RuntimeConfigSnapshot(String degradeSwitch, String rateLimiterSwitch) {
            this.degradeSwitch = degradeSwitch;
            this.rateLimiterSwitch = rateLimiterSwitch;
        }

        /** 返回快照中的抽奖降级开关值。 */
        public String getDegradeSwitch() {
            return degradeSwitch;
        }

        /** 返回快照中的限流开关值。 */
        public String getRateLimiterSwitch() {
            return rateLimiterSwitch;
        }
    }
}

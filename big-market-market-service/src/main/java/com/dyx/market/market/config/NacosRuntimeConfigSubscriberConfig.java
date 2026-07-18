package com.dyx.market.market.config;

import com.alibaba.nacos.api.config.listener.AbstractListener;
import com.dyx.market.management.config.NacosConfigSyncService;
import com.dyx.market.management.config.PlatformConfigChangeNotifier;
import com.dyx.market.types.config.RuntimeConfigHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;

/**
 * Subscribes the market request path to the Nacos runtime-switch DataId,
 * with a Redis pub/sub fallback for Nacos 3.x empty/public listener gaps.
 */
@Slf4j
@Configuration
@ConditionalOnProperty(value = "nacos.config.sync.enabled", havingValue = "true")
public class NacosRuntimeConfigSubscriberConfig {

    @Resource
    private NacosConfigSyncService nacosConfigSyncService;

    @Resource
    private RuntimeConfigHolder runtimeConfigHolder;

    @Autowired(required = false)
    private RedissonClient redissonClient;

    @PostConstruct
    public void initialize() {
        nacosConfigSyncService.addRuntimeSwitchesListener(new AbstractListener() {
            @Override
            public void receiveConfigInfo(String configInfo) {
                refresh(configInfo, "listener");
            }
        });

        if (redissonClient != null) {
            redissonClient.getTopic(PlatformConfigChangeNotifier.RUNTIME_TOPIC)
                    .addListener(String.class, (channel, msg) -> refresh(msg, "pubsub"));
            log.info("Subscribed Redis topic {} for runtime switch fan-out",
                    PlatformConfigChangeNotifier.RUNTIME_TOPIC);
        }

        refresh(nacosConfigSyncService.fetchRuntimeSwitches(3000), "startup");
    }

    private void refresh(String content, String source) {
        try {
            runtimeConfigHolder.refreshFromContent(content);
            log.info("Runtime switches refreshed from Nacos ({}) degradeSwitch={}, rateLimiterSwitch={}",
                    source, runtimeConfigHolder.getDegradeSwitch(), runtimeConfigHolder.getRateLimiterSwitch());
        } catch (RuntimeException e) {
            runtimeConfigHolder.refreshFromContent(null);
            log.warn("Invalid runtime switches from Nacos ({}); reset to safe defaults: {}", source, e.getMessage());
        }
    }
}

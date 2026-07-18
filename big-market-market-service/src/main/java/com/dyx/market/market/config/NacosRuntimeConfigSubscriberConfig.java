package com.dyx.market.market.config;

import com.alibaba.nacos.api.config.listener.AbstractListener;
import com.dyx.market.management.config.NacosConfigSyncService;
import com.dyx.market.types.config.RuntimeConfigHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

import javax.annotation.Resource;

/**
 * Subscribes the market request path to the Nacos runtime-switch DataId.
 */
@Slf4j
@Configuration
@ConditionalOnProperty(value = "nacos.config.sync.enabled", havingValue = "true")
public class NacosRuntimeConfigSubscriberConfig {

    @Resource
    private NacosConfigSyncService nacosConfigSyncService;

    @Resource
    private RuntimeConfigHolder runtimeConfigHolder;

    @javax.annotation.PostConstruct
    public void initialize() {
        nacosConfigSyncService.addRuntimeSwitchesListener(new AbstractListener() {
            @Override
            public void receiveConfigInfo(String configInfo) {
                refresh(configInfo, "listener");
            }
        });

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

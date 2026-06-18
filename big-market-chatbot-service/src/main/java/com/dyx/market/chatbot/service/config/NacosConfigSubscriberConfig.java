package com.dyx.market.chatbot.service.config;

import com.alibaba.nacos.api.config.listener.AbstractListener;
import com.dyx.market.management.config.NacosConfigSyncService;
import com.dyx.market.management.config.PlatformConfigService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.Resource;

/**
 * 订阅 admin-service 发布的 Nacos 平台配置变更。
 * <p>
 * 仅当上下文中存在 {@link NacosConfigSyncService} 时生效（{@code nacos.config.sync.enabled=true}）。
 */
@Slf4j
@Configuration
@ConditionalOnBean(NacosConfigSyncService.class)
public class NacosConfigSubscriberConfig implements InitializingBean {

    @Resource
    private NacosConfigSyncService nacosConfigSyncService;

    @Resource
    private PlatformConfigService platformConfigService;

    @Override
    public void afterPropertiesSet() {
        String current = nacosConfigSyncService.fetchCurrent(3000);
        if (StringUtils.isNotBlank(current)) {
            platformConfigService.refreshFromContent(current);
            log.info("Loaded platform config from Nacos on chatbot-service startup");
        } else {
            log.info("No platform config in Nacos yet — using local defaults");
        }

        nacosConfigSyncService.addListener(new AbstractListener() {
            @Override
            public void receiveConfigInfo(String configInfo) {
                log.info("Platform config update received from Nacos");
                platformConfigService.refreshFromContent(configInfo);
            }
        });
    }
}

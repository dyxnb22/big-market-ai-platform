package com.dyx.market.chatbot.service.config;

import com.alibaba.nacos.api.config.listener.AbstractListener;
import com.dyx.market.management.config.NacosConfigSyncService;
import com.dyx.market.management.config.PlatformConfigChangeNotifier;
import com.dyx.market.management.config.PlatformConfigService;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.Resource;

/**
 * 订阅 admin-service 发布的 Nacos 平台配置变更。
 * <p>
 * 仅当上下文中存在 {@link NacosConfigSyncService} 时生效（{@code nacos.config.sync.enabled=true}）。
 * Redis pub/sub 作为 Nacos 3.x empty/public listener 间隙的可靠 fan-out。
 */
@Slf4j
@Configuration
@ConditionalOnBean(NacosConfigSyncService.class)
public class NacosConfigSubscriberConfig implements InitializingBean {

    @Resource
    private NacosConfigSyncService nacosConfigSyncService;

    @Resource
    private PlatformConfigService platformConfigService;

    @Autowired(required = false)
    private RedissonClient redissonClient;

    @Override
    public void afterPropertiesSet() {
        platformConfigService.refreshPlatformFromContent(nacosConfigSyncService.fetchCurrent(3000));
        log.info("Loaded complete platform config snapshot from Nacos on chatbot-service startup");

        nacosConfigSyncService.addListener(new AbstractListener() {
            @Override
            public void receiveConfigInfo(String configInfo) {
                log.info("Platform config update received from Nacos");
                platformConfigService.refreshPlatformFromContent(configInfo);
            }
        });

        if (redissonClient != null) {
            redissonClient.getTopic(PlatformConfigChangeNotifier.PLATFORM_TOPIC)
                    .addListener(String.class, (channel, msg) -> {
                        log.info("Platform config update received from Nacos");
                        platformConfigService.refreshPlatformFromContent(msg);
                    });
            log.info("Subscribed Redis topic {} for platform config fan-out",
                    PlatformConfigChangeNotifier.PLATFORM_TOPIC);
        }
    }
}

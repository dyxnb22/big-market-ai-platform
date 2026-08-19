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
 * 将 market 请求链路订阅到 Nacos 运行时开关 DataId，并以 Redis 发布/订阅兜底
 * Nacos 3.x 空租户/public 监听可能漏通知的问题。
 */
@Slf4j
@Configuration
@ConditionalOnProperty(value = "nacos.config.sync.enabled", havingValue = "true")
public class NacosRuntimeConfigSubscriberConfig {

    @Resource
    /** Nacos 配置监听与读取桥接器。 */
    private NacosConfigSyncService nacosConfigSyncService;

    @Resource
    /** market 请求链路读取的不可变运行时开关快照。 */
    private RuntimeConfigHolder runtimeConfigHolder;

    @Autowired(required = false)
    /** Redis 广播客户端；未配置时仅使用 Nacos 监听器。 */
    private RedissonClient redissonClient;

    @PostConstruct
    /** 注册 Nacos/Redis 两路监听，并用当前 Nacos 内容初始化本地快照。 */
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

    /** 解析并刷新运行时开关；内容非法时恢复安全默认值。 */
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

package com.dyx.market.management.config;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.exception.NacosException;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Properties;

/**
 * Optional Nacos config sync bridge.
 * Enabled by setting nacos.config.sync.enabled=true.
 * Admin-service uses publish(); chatbot-service registers a listener via addListener().
 */
@Service
@ConditionalOnProperty(value = "nacos.config.sync.enabled", havingValue = "true")
public class NacosConfigSyncService implements InitializingBean, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(NacosConfigSyncService.class);

    @Value("${nacos.config.sync.serverAddr:127.0.0.1:8848}")
    private String serverAddr;

    @Value("${nacos.config.sync.namespace:}")
    private String namespace;

    @Value("${nacos.config.sync.dataId:big-market-platform-config}")
    private String dataId;

    @Value("${nacos.config.sync.group:DEFAULT_GROUP}")
    private String group;

    private ConfigService configService;

    @Override
    public void afterPropertiesSet() {
        try {
            Properties nacosProps = new Properties();
            nacosProps.put(PropertyKeyConst.SERVER_ADDR, serverAddr);
            if (StringUtils.isNotBlank(namespace)) {
                nacosProps.put(PropertyKeyConst.NAMESPACE, namespace);
            }
            configService = NacosFactory.createConfigService(nacosProps);
            log.info("NacosConfigSyncService initialized, serverAddr={}, dataId={}", serverAddr, dataId);
        } catch (NacosException e) {
            log.warn("NacosConfigSyncService init failed (will skip sync): {}", e.getMessage());
        }
    }

    public void publish(String content) {
        if (configService == null) {
            return;
        }
        try {
            boolean ok = configService.publishConfig(dataId, group, content);
            if (ok) {
                log.info("Published platform config to Nacos dataId={}", dataId);
            } else {
                log.warn("Nacos publishConfig returned false, dataId={}", dataId);
            }
        } catch (NacosException e) {
            log.warn("Failed to publish platform config to Nacos: {}", e.getMessage());
        }
    }

    public String fetchCurrent(long timeoutMs) {
        if (configService == null) {
            return null;
        }
        try {
            return configService.getConfig(dataId, group, timeoutMs);
        } catch (NacosException e) {
            log.warn("Failed to fetch platform config from Nacos: {}", e.getMessage());
            return null;
        }
    }

    public void addListener(Listener listener) {
        if (configService == null) {
            return;
        }
        try {
            configService.addListener(dataId, group, listener);
            log.info("Registered Nacos config listener, dataId={}", dataId);
        } catch (NacosException e) {
            log.warn("Failed to register Nacos listener: {}", e.getMessage());
        }
    }

    @Override
    public void destroy() {
        try {
            if (configService != null) {
                configService.shutDown();
            }
        } catch (Exception ignored) {
        }
    }
}

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
 * 可选的 Nacos 配置同步桥接。
 * <p>
 * 通过 {@code nacos.config.sync.enabled=true} 启用；
 * admin-service 调用 {@link #publish} 或 {@link #publishRuntimeSwitches} 推送，
 * chatbot-service / market-service 通过对应 listener 订阅变更。
 * <p>
 * Publish and listener registration are fail-closed: Nacos is the only runtime
 * source of platform configuration, so a local shadow write is never accepted.
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

    @Value("${nacos.config.sync.runtimeDataId:big-market-runtime-switches}")
    private String runtimeDataId;

    @Value("${nacos.config.sync.runtimeGroup:DEFAULT_GROUP}")
    private String runtimeGroup;

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
            log.info("NacosConfigSyncService initialized, serverAddr={}, dataId={}, runtimeDataId={}",
                    serverAddr, dataId, runtimeDataId);
        } catch (NacosException e) {
            throw new IllegalStateException("Nacos config service initialization failed", e);
        }
    }

    /**
     * Publish platform config to Nacos.
     *
     * @return true when publish succeeded
     * @throws IllegalStateException when publish fails
     */
    public boolean publish(String content) {
        return publish(dataId, group, content);
    }

    /** Publish runtime switches to the dedicated Nacos data item. */
    public boolean publishRuntimeSwitches(String content) {
        return publish(runtimeDataId, runtimeGroup, content);
    }

    private boolean publish(String targetDataId, String targetGroup, String content) {
        if (configService == null) {
            throw new IllegalStateException("Nacos config service unavailable; publish rejected (fail-closed)");
        }
        try {
            boolean ok = configService.publishConfig(targetDataId, targetGroup, content);
            if (ok) {
                log.info("Published config to Nacos dataId={}, group={}", targetDataId, targetGroup);
                return true;
            }
            throw new IllegalStateException("Nacos publishConfig returned false for dataId=" + targetDataId);
        } catch (NacosException e) {
            throw new IllegalStateException("Failed to publish platform config to Nacos: " + e.getMessage(), e);
        }
    }

    public String fetchCurrent(long timeoutMs) {
        return fetchCurrent(dataId, group, timeoutMs);
    }

    public String fetchRuntimeSwitches(long timeoutMs) {
        return fetchCurrent(runtimeDataId, runtimeGroup, timeoutMs);
    }

    private String fetchCurrent(String targetDataId, String targetGroup, long timeoutMs) {
        if (configService == null) {
            throw new IllegalStateException("Nacos config service unavailable; startup config fetch rejected");
        }
        try {
            return configService.getConfig(targetDataId, targetGroup, timeoutMs);
        } catch (NacosException e) {
            throw new IllegalStateException("Failed to fetch required Nacos config dataId=" + targetDataId, e);
        }
    }

    public void addListener(Listener listener) {
        addListener(dataId, group, listener);
    }

    public void addRuntimeSwitchesListener(Listener listener) {
        addListener(runtimeDataId, runtimeGroup, listener);
    }

    private void addListener(String targetDataId, String targetGroup, Listener listener) {
        if (configService == null) {
            throw new IllegalStateException("Nacos config service unavailable; listener registration rejected");
        }
        try {
            configService.addListener(targetDataId, targetGroup, listener);
            log.info("Registered Nacos config listener, dataId={}, group={}", targetDataId, targetGroup);
        } catch (NacosException e) {
            throw new IllegalStateException("Failed to register Nacos listener, dataId=" + targetDataId, e);
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

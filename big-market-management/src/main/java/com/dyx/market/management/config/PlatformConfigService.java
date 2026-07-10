package com.dyx.market.management.config;

import com.dyx.market.trigger.api.dto.AdminConfigRequestDTO;
import com.dyx.market.trigger.api.dto.AdminConfigResponseDTO;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 平台运行时配置的轻量存储（学习/产品化版本）。
 * <p>
 * 原项目使用 Zookeeper DCC 做运行时开关；本服务为 admin、chatbot 等模块提供
 * 本地可见的配置源，后续可替换为 MySQL 或正式配置中心。
 */
@Service
public class PlatformConfigService implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(PlatformConfigService.class);

    private static final String DEFAULT_STORE_PATH = "data/platform-config.properties";

    private final ConcurrentMap<String, AdminConfigResponseDTO> configStore = new ConcurrentHashMap<>();

    @Autowired(required = false)
    private NacosConfigSyncService nacosConfigSyncService;

    @Autowired(required = false)
    private DynamicConfigSyncPort dynamicConfigSyncPort;

    @Override
    public void afterPropertiesSet() throws Exception {
        putDefault("chatbot", "enabled",  "true",                        "Chatbot entrance switch");
        putDefault("chatbot", "provider", "local",                       "Provider: local, deepseek, openai");
        putDefault("chatbot", "apiKey",   "",                            "LLM provider API key");
        putDefault("chatbot", "baseUrl",  "https://api.deepseek.com",    "LLM provider base URL");
        putDefault("chatbot", "model",    "deepseek-chat",               "LLM model name");
        putDefault("system",  "degradeSwitch",    "close", "Global raffle degrade switch");
        putDefault("system",  "rateLimiterSwitch","close", "Global rate limiter switch");
        loadFromDisk();
        // 启动时以 Nacos 为权威源：用远端最新配置覆盖本地磁盘快照
        if (nacosConfigSyncService != null) {
            String nacosContent = nacosConfigSyncService.fetchCurrent(3000);
            if (nacosContent != null && !nacosContent.isEmpty()) {
                refreshFromContent(nacosContent);
                log.info("Platform config restored from Nacos on startup ({} entries)", configStore.size());
            }
        }
    }

    public List<AdminConfigResponseDTO> list(String namespace) {
        List<AdminConfigResponseDTO> values = new ArrayList<>();
        for (AdminConfigResponseDTO config : configStore.values()) {
            if (StringUtils.isBlank(namespace) || namespace.equals(config.getNamespace())) {
                values.add(config);
            }
        }
        values.sort(Comparator.comparing(AdminConfigResponseDTO::getNamespace).thenComparing(AdminConfigResponseDTO::getConfigKey));
        return values;
    }

    public AdminConfigResponseDTO get(String namespace, String configKey) {
        return configStore.get(storeKey(namespace, configKey));
    }

    public String getValue(String namespace, String configKey, String defaultValue) {
        AdminConfigResponseDTO config = get(namespace, configKey);
        return config == null || StringUtils.isBlank(config.getConfigValue()) ? defaultValue : config.getConfigValue();
    }

    public synchronized AdminConfigResponseDTO save(AdminConfigRequestDTO request) throws IOException {
        AdminConfigResponseDTO config = AdminConfigResponseDTO.builder()
                .namespace(request.getNamespace())
                .configKey(request.getConfigKey())
                .configValue(request.getConfigValue())
                .description(request.getDescription())
                .updateTime(System.currentTimeMillis())
                .build();
        configStore.put(storeKey(config.getNamespace(), config.getConfigKey()), config);
        saveToDisk();
        publishToNacos();
        syncDynamicConfigIfNeeded(config);
        return config;
    }

    public synchronized void delete(String namespace, String configKey) throws IOException {
        configStore.remove(storeKey(namespace, configKey));
        saveToDisk();
        publishToNacos();
    }

    public synchronized void refreshFromContent(String content) {
        if (StringUtils.isBlank(content)) {
            return;
        }
        try {
            Properties properties = new Properties();
            properties.load(new StringReader(content));
            for (String propertyName : properties.stringPropertyNames()) {
                String[] parts = propertyName.split("\\.", 3);
                if (parts.length != 3 || !"value".equals(parts[2])) {
                    continue;
                }
                String namespace = parts[0];
                String configKey = parts[1];
                String description = properties.getProperty(namespace + "." + configKey + ".description", "");
                configStore.put(storeKey(namespace, configKey), AdminConfigResponseDTO.builder()
                        .namespace(namespace)
                        .configKey(configKey)
                        .configValue(properties.getProperty(propertyName))
                        .description(description)
                        .updateTime(System.currentTimeMillis())
                        .build());
            }
            log.info("Platform config refreshed from Nacos content ({} entries)", configStore.size());
        } catch (Exception e) {
            log.warn("Failed to parse config content from Nacos: {}", e.getMessage());
        }
    }

    private void putDefault(String namespace, String key, String value, String description) {
        configStore.put(storeKey(namespace, key), AdminConfigResponseDTO.builder()
                .namespace(namespace)
                .configKey(key)
                .configValue(value)
                .description(description)
                .updateTime(System.currentTimeMillis())
                .build());
    }

    private void loadFromDisk() throws IOException {
        File file = storeFile();
        if (!file.exists()) {
            saveToDisk();
            return;
        }
        Properties properties = new Properties();
        try (FileInputStream inputStream = new FileInputStream(file)) {
            properties.load(inputStream);
        }
        for (String propertyName : properties.stringPropertyNames()) {
            String[] parts = propertyName.split("\\.", 3);
            if (parts.length != 3 || !"value".equals(parts[2])) {
                continue;
            }
            String namespace = parts[0];
            String configKey = parts[1];
            String description = properties.getProperty(namespace + "." + configKey + ".description", "");
            configStore.put(storeKey(namespace, configKey), AdminConfigResponseDTO.builder()
                    .namespace(namespace)
                    .configKey(configKey)
                    .configValue(properties.getProperty(propertyName))
                    .description(description)
                    .updateTime(System.currentTimeMillis())
                    .build());
        }
    }

    private void saveToDisk() throws IOException {
        File file = storeFile();
        File parentFile = file.getParentFile();
        if (parentFile != null && !parentFile.exists()) {
            parentFile.mkdirs();
        }
        try (FileOutputStream outputStream = new FileOutputStream(file)) {
            buildProperties().store(outputStream, "Big Market platform runtime configuration");
        }
    }

    private void syncDynamicConfigIfNeeded(AdminConfigResponseDTO config) {
        if (dynamicConfigSyncPort == null) {
            return;
        }
        if ("system".equals(config.getNamespace()) && "degradeSwitch".equals(config.getConfigKey())) {
            dynamicConfigSyncPort.syncDegradeSwitch(config.getConfigValue());
        }
        if ("system".equals(config.getNamespace()) && "rateLimiterSwitch".equals(config.getConfigKey())) {
            dynamicConfigSyncPort.syncRateLimiterSwitch(config.getConfigValue());
        }
    }

    private void publishToNacos() {
        if (nacosConfigSyncService == null) {
            return;
        }
        try {
            StringWriter writer = new StringWriter();
            buildProperties().store(writer, "Big Market platform runtime configuration");
            nacosConfigSyncService.publish(writer.toString());
        } catch (Exception e) {
            log.warn("Failed to serialize config for Nacos publish: {}", e.getMessage());
        }
    }

    private Properties buildProperties() {
        Properties properties = new Properties();
        for (AdminConfigResponseDTO config : configStore.values()) {
            String prefix = config.getNamespace() + "." + config.getConfigKey();
            properties.setProperty(prefix + ".value", StringUtils.defaultString(config.getConfigValue()));
            properties.setProperty(prefix + ".description", StringUtils.defaultString(config.getDescription()));
        }
        return properties;
    }

    private File storeFile() {
        return new File(System.getProperty("big.market.config.store", DEFAULT_STORE_PATH));
    }

    private String storeKey(String namespace, String configKey) {
        return namespace + ":" + configKey;
    }
}

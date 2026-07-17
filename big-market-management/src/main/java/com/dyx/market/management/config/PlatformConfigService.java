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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 平台运行时配置的轻量存储（学习/产品化版本）。
 * <p>
 * Admin 使用本地快照提供管理查询，Nacos 负责跨服务配置同步。
 * 普通平台配置与运行时开关分别发布到不同的 Nacos DataId，避免敏感的
 * chatbot 配置被不需要它的服务订阅。
 */
@Service
public class PlatformConfigService implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(PlatformConfigService.class);

    private static final String DEFAULT_STORE_PATH = "data/platform-config.properties";

    private final ConcurrentMap<String, AdminConfigResponseDTO> configStore = new ConcurrentHashMap<>();

    @Autowired(required = false)
    private NacosConfigSyncService nacosConfigSyncService;

    @Override
    public void afterPropertiesSet() throws Exception {
        putDefault("chatbot", "enabled",  "true",                        "Chatbot entrance switch");
        putDefault("chatbot", "provider", "local",                       "Provider: local, deepseek, openai");
        putDefault("chatbot", "apiKey",   "",                            "LLM provider API key");
        putDefault("chatbot", "baseUrl",  "https://api.deepseek.com",    "LLM provider base URL");
        putDefault("chatbot", "model",    "deepseek-chat",               "LLM model name");
        putDefault("system",  "degradeSwitch",    "close", "Global raffle degrade switch");
        putDefault("system",  "rateLimiterSwitch","close", "Global rate limiter switch");
        putDefault("activity.100301", "state", "online", "Demo activity display state");
        putDefault("activity.100301", "title", "幸运轮盘活动", "Demo activity title");
        putDefault("activity.100301", "copy", "登录参与抽奖，AI 帮你解读活动权益。", "Demo activity copy");
        putDefault("activity.100401", "state", "online", "Staged demo activity display state");
        putDefault("activity.100401", "title", "OpenAi抽奖活动", "Staged demo activity title");
        putDefault("activity.100401", "copy", "登录参与抽奖，AI 帮你解读活动权益。", "Staged demo activity copy");
        loadFromDisk();
        // 启动时以 Nacos 为权威源：用远端最新配置覆盖本地磁盘快照
        if (nacosConfigSyncService != null) {
            String nacosContent = nacosConfigSyncService.fetchCurrent(3000);
            if (nacosContent != null && !nacosContent.isEmpty()) {
                refreshFromContent(nacosContent);
                log.info("Platform config restored from Nacos on startup ({} entries)", configStore.size());
            }
            String runtimeContent = nacosConfigSyncService.fetchRuntimeSwitches(3000);
            if (runtimeContent != null && !runtimeContent.isEmpty()) {
                refreshFromContent(runtimeContent);
                log.info("Runtime switches restored from Nacos on startup");
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
        if (config != null && "__DELETED__".equals(config.getDescription())) {
            return defaultValue;
        }
        return config == null || StringUtils.isBlank(config.getConfigValue()) ? defaultValue : config.getConfigValue();
    }

    public synchronized AdminConfigResponseDTO save(AdminConfigRequestDTO request) throws IOException {
        String key = storeKey(request.getNamespace(), request.getConfigKey());
        AdminConfigResponseDTO previous = configStore.get(key);
        AdminConfigResponseDTO config = AdminConfigResponseDTO.builder()
                .namespace(request.getNamespace())
                .configKey(request.getConfigKey())
                .configValue(request.getConfigValue())
                .description(request.getDescription())
                .updateTime(System.currentTimeMillis())
                .build();
        configStore.put(key, config);
        try {
            saveToDisk();
            boolean runtimeConfig = isRuntimeConfig(config);
            String content = serializePropertiesContent(runtimeConfig);
            String contentHash = contentHash(content);
            boolean nacosPublished = publishToNacos(content, runtimeConfig);
            String source = nacosConfigSyncService == null ? "local" : (nacosPublished ? "nacos" : "local");
            return config.toBuilder()
                    .contentHash(contentHash)
                    .nacosPublished(nacosPublished)
                    .source(source)
                    .build();
        } catch (RuntimeException e) {
            rollbackConfig(key, previous);
            throw e;
        } catch (IOException e) {
            rollbackConfig(key, previous);
            throw e;
        }
    }

    private void rollbackConfig(String key, AdminConfigResponseDTO previous) throws IOException {
        if (previous != null) {
            configStore.put(key, previous);
        } else {
            configStore.remove(key);
        }
        saveToDisk();
        publishToNacosBestEffort(namespaceFromStoreKey(key));
    }

    public synchronized void delete(String namespace, String configKey) throws IOException {
        String key = storeKey(namespace, configKey);
        AdminConfigResponseDTO previous = configStore.get(key);
        AdminConfigResponseDTO tombstone = AdminConfigResponseDTO.builder()
                .namespace(namespace)
                .configKey(configKey)
                .configValue("")
                .description("__DELETED__")
                .updateTime(System.currentTimeMillis())
                .build();
        configStore.put(key, tombstone);
        try {
            saveToDisk();
            boolean runtimeConfig = "system".equals(namespace);
            String content = serializePropertiesContent(runtimeConfig);
            publishToNacos(content, runtimeConfig);
        } catch (IOException e) {
            rollbackConfig(key, previous);
            throw e;
        }
    }

    public synchronized void refreshFromContent(String content) {
        if (StringUtils.isBlank(content)) {
            return;
        }
        try {
            Properties properties = new Properties();
            properties.load(new StringReader(content));
            for (String propertyName : properties.stringPropertyNames()) {
                if (!propertyName.endsWith(".value")) {
                    continue;
                }
                String withoutValue = propertyName.substring(0, propertyName.length() - ".value".length());
                int lastDot = withoutValue.lastIndexOf('.');
                if (lastDot < 0) {
                    continue;
                }
                String namespace = withoutValue.substring(0, lastDot);
                String configKey = withoutValue.substring(lastDot + 1);
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
            if (!propertyName.endsWith(".value")) {
                continue;
            }
            String withoutValue = propertyName.substring(0, propertyName.length() - ".value".length());
            int lastDot = withoutValue.lastIndexOf('.');
            if (lastDot < 0) {
                continue;
            }
            String namespace = withoutValue.substring(0, lastDot);
            String configKey = withoutValue.substring(lastDot + 1);
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

    private void publishToNacosBestEffort(String namespace) {
        if (nacosConfigSyncService == null) {
            return;
        }
        try {
            boolean runtimeConfig = "system".equals(namespace);
            publishToNacos(serializePropertiesContent(runtimeConfig), runtimeConfig);
        } catch (Exception e) {
            log.warn("Best-effort Nacos publish after rollback failed: {}", e.getMessage());
        }
    }

    private String serializePropertiesContent(boolean runtimeOnly) throws IOException {
        StringWriter writer = new StringWriter();
        buildProperties(runtimeOnly).store(writer,
                runtimeOnly ? "Big Market runtime switches" : "Big Market platform configuration");
        return writer.toString();
    }

    private boolean publishToNacos(String content, boolean runtimeConfig) throws IOException {
        if (nacosConfigSyncService == null) {
            return false;
        }
        try {
            return runtimeConfig
                    ? nacosConfigSyncService.publishRuntimeSwitches(content)
                    : nacosConfigSyncService.publish(content);
        } catch (IllegalStateException e) {
            throw new IOException(e.getMessage(), e);
        } catch (Exception e) {
            throw new IOException("Failed to publish config to Nacos: " + e.getMessage(), e);
        }
    }

    private String contentHash(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                sb.append(String.format("%02x", hash[i]));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private Properties buildProperties(boolean runtimeOnly) {
        Properties properties = new Properties();
        for (AdminConfigResponseDTO config : configStore.values()) {
            if (runtimeOnly != isRuntimeConfig(config)) {
                continue;
            }
            String prefix = config.getNamespace() + "." + config.getConfigKey();
            properties.setProperty(prefix + ".value", StringUtils.defaultString(config.getConfigValue()));
            properties.setProperty(prefix + ".description", StringUtils.defaultString(config.getDescription()));
        }
        return properties;
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

    private boolean isRuntimeConfig(AdminConfigResponseDTO config) {
        return config != null && "system".equals(config.getNamespace());
    }

    private String namespaceFromStoreKey(String key) {
        int separator = key.indexOf(':');
        return separator < 0 ? key : key.substring(0, separator);
    }

    private File storeFile() {
        return new File(System.getProperty("big.market.config.store", DEFAULT_STORE_PATH));
    }

    private String storeKey(String namespace, String configKey) {
        return namespace + ":" + configKey;
    }
}

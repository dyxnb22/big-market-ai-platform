package com.dyx.market.management.config;

import com.dyx.market.trigger.api.dto.AdminConfigRequestDTO;
import com.dyx.market.trigger.api.dto.AdminConfigResponseDTO;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Lightweight configuration store for the learning/productized version.
 *
 * <p>The original project used Zookeeper DCC for runtime switches. This service
 * gives the admin and chatbot modules a local, visible configuration source
 * that can later be replaced by MySQL or a formal config center.</p>
 */
@Service
public class PlatformConfigService implements InitializingBean {

    private static final String DEFAULT_STORE_PATH = "data/platform-config.properties";

    private final ConcurrentMap<String, AdminConfigResponseDTO> configStore = new ConcurrentHashMap<>();

    @Override
    public void afterPropertiesSet() throws Exception {
        putDefault("chatbot", "enabled", "true", "Chatbot entrance switch");
        putDefault("chatbot", "model", "rule-based", "Default assistant engine before LLM provider is configured");
        putDefault("chatbot", "provider", "local", "Provider: local, openai, deepseek");
        putDefault("system", "degradeSwitch", "close", "Global raffle degrade switch");
        putDefault("system", "rateLimiterSwitch", "close", "Global rate limiter switch");
        loadFromDisk();
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
        return config;
    }

    public synchronized void delete(String namespace, String configKey) throws IOException {
        configStore.remove(storeKey(namespace, configKey));
        saveToDisk();
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
        Properties properties = new Properties();
        for (AdminConfigResponseDTO config : configStore.values()) {
            String prefix = config.getNamespace() + "." + config.getConfigKey();
            properties.setProperty(prefix + ".value", StringUtils.defaultString(config.getConfigValue()));
            properties.setProperty(prefix + ".description", StringUtils.defaultString(config.getDescription()));
        }
        try (FileOutputStream outputStream = new FileOutputStream(file)) {
            properties.store(outputStream, "Big Market platform runtime configuration");
        }
    }

    private File storeFile() {
        return new File(System.getProperty("big.market.config.store", DEFAULT_STORE_PATH));
    }

    private String storeKey(String namespace, String configKey) {
        return namespace + ":" + configKey;
    }
}

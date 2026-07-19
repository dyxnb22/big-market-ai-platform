package com.dyx.market.management.config;

import com.dyx.market.trigger.api.dto.AdminConfigRequestDTO;
import com.dyx.market.trigger.api.dto.AdminConfigResponseDTO;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Platform configuration cache backed exclusively by Nacos.
 *
 * <p>The in-process map is only an immutable read snapshot of the two Nacos
 * DataIds. It is never persisted to a local file and a failed publish never
 * reports a successful admin save. Nacos persistence is the commit point;
 * Redis fan-out is a retriable delivery hint and never rolls back a committed
 * Nacos write. Safe compiled defaults are used only when a key is deleted or
 * an empty/invalid Nacos payload must be interpreted.</p>
 */
@Service
public class PlatformConfigService implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(PlatformConfigService.class);

    private final AtomicReference<Map<String, AdminConfigResponseDTO>> configSnapshot =
            new AtomicReference<>(Collections.<String, AdminConfigResponseDTO>emptyMap());

    @Autowired(required = false)
    private NacosConfigSyncService nacosConfigSyncService;

    @Autowired(required = false)
    private PlatformConfigChangeNotifier platformConfigChangeNotifier;

    @Override
    public void afterPropertiesSet() {
        configSnapshot.set(immutable(defaultConfigs()));
        if (nacosConfigSyncService == null) {
            // Test contexts can omit Nacos. Runtime services enable the Nacos bridge
            // and reject writes when it is unavailable.
            return;
        }
        refreshPlatformFromContent(nacosConfigSyncService.fetchCurrent(3000));
        refreshRuntimeFromContent(nacosConfigSyncService.fetchRuntimeSwitches(3000));
    }

    public List<AdminConfigResponseDTO> list(String namespace) {
        List<AdminConfigResponseDTO> values = new ArrayList<>();
        for (AdminConfigResponseDTO config : configSnapshot.get().values()) {
            if (StringUtils.isBlank(namespace) || namespace.equals(config.getNamespace())) {
                values.add(withCurrentMetadata(config));
            }
        }
        values.sort(Comparator.comparing(AdminConfigResponseDTO::getNamespace)
                .thenComparing(AdminConfigResponseDTO::getConfigKey));
        return values;
    }

    public AdminConfigResponseDTO get(String namespace, String configKey) {
        AdminConfigResponseDTO config = configSnapshot.get().get(storeKey(namespace, configKey));
        return config == null ? null : withCurrentMetadata(config);
    }

    public String getValue(String namespace, String configKey, String defaultValue) {
        AdminConfigResponseDTO config = get(namespace, configKey);
        if (config != null && "__DELETED__".equals(config.getDescription())) {
            return defaultValue;
        }
        return config == null || StringUtils.isBlank(config.getConfigValue())
                ? defaultValue : config.getConfigValue();
    }

    /** Returns one immutable configuration generation for request-scoped reads. */
    public Map<String, String> snapshotValues(String namespace) {
        Map<String, String> values = new LinkedHashMap<>();
        for (AdminConfigResponseDTO config : configSnapshot.get().values()) {
            if (namespace.equals(config.getNamespace()) && !"__DELETED__".equals(config.getDescription())) {
                values.put(config.getConfigKey(), config.getConfigValue());
            }
        }
        return Collections.unmodifiableMap(values);
    }

    public synchronized AdminConfigResponseDTO save(AdminConfigRequestDTO request) throws IOException {
        AdminConfigResponseDTO config = AdminConfigResponseDTO.builder()
                .namespace(request.getNamespace())
                .configKey(request.getConfigKey())
                .configValue(request.getConfigValue())
                .description(request.getDescription())
                .updateTime(System.currentTimeMillis())
                .build();
        Map<String, AdminConfigResponseDTO> candidate = new LinkedHashMap<>(configSnapshot.get());
        candidate.put(storeKey(config.getNamespace(), config.getConfigKey()), config);

        boolean runtimeConfig = isRuntimeConfig(config);
        verifyExpectedContentHash(request.getExpectedContentHash(), runtimeConfig);
        String content = serializePropertiesContent(candidate, runtimeConfig);
        boolean notificationPending = !publishToNacos(content, runtimeConfig);
        configSnapshot.set(immutable(candidate));
        return config.toBuilder()
                .contentHash(contentHash(content))
                .nacosPublished(true)
                .notificationPending(notificationPending)
                .source("nacos")
                .build();
    }

    public synchronized void delete(String namespace, String configKey) throws IOException {
        AdminConfigRequestDTO request = new AdminConfigRequestDTO();
        request.setNamespace(namespace);
        request.setConfigKey(configKey);
        delete(request);
    }

    /** Deletes a key with the same optimistic-concurrency contract as save. */
    public synchronized void delete(AdminConfigRequestDTO request) throws IOException {
        AdminConfigResponseDTO tombstone = AdminConfigResponseDTO.builder()
                .namespace(request.getNamespace())
                .configKey(request.getConfigKey())
                .configValue("")
                .description("__DELETED__")
                .updateTime(System.currentTimeMillis())
                .build();
        Map<String, AdminConfigResponseDTO> candidate = new LinkedHashMap<>(configSnapshot.get());
        candidate.put(storeKey(request.getNamespace(), request.getConfigKey()), tombstone);
        boolean runtimeConfig = isRuntimeConfig(tombstone);
        verifyExpectedContentHash(request.getExpectedContentHash(), runtimeConfig);
        boolean notificationPending = !publishToNacos(
                serializePropertiesContent(candidate, runtimeConfig), runtimeConfig);
        configSnapshot.set(immutable(candidate));
        if (notificationPending) {
            log.warn("Deleted config committed to Nacos but Redis fan-out is pending: namespace={}, key={}",
                    request.getNamespace(), request.getConfigKey());
        }
    }

    /** Replaces the complete non-runtime snapshot from one Nacos payload. */
    public void refreshPlatformFromContent(String content) {
        replaceScope(content, false);
    }

    /** Replaces the complete runtime-switch snapshot from one Nacos payload. */
    public void refreshRuntimeFromContent(String content) {
        replaceScope(content, true);
    }

    /** Compatibility entry point for callers that receive one DataId at a time. */
    public void refreshFromContent(String content) {
        Properties properties = parseProperties(content);
        boolean hasRuntime = containsRuntimeConfig(properties);
        boolean hasPlatform = containsPlatformConfig(properties);
        if (hasRuntime) {
            replaceScope(properties, true);
        }
        if (hasPlatform || (!hasRuntime && !hasPlatform)) {
            replaceScope(properties, false);
        }
    }

    private void replaceScope(String content, boolean runtimeOnly) {
        replaceScope(parseProperties(content), runtimeOnly);
    }

    private void replaceScope(Properties properties, boolean runtimeOnly) {
        Map<String, AdminConfigResponseDTO> nextScope = defaultsForScope(runtimeOnly);
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
            if (runtimeOnly != "system".equals(namespace)) {
                continue;
            }
            String description = properties.getProperty(namespace + "." + configKey + ".description", "");
            if ("__DELETED__".equals(description)) {
                // A deletion restores the scope's safe default and must not leave
                // an old in-memory value active.
                continue;
            }
            nextScope.put(storeKey(namespace, configKey), AdminConfigResponseDTO.builder()
                    .namespace(namespace)
                    .configKey(configKey)
                    .configValue(properties.getProperty(propertyName))
                    .description(description)
                    .updateTime(System.currentTimeMillis())
                    .build());
        }

        Map<String, AdminConfigResponseDTO> next = new LinkedHashMap<>();
        for (AdminConfigResponseDTO config : configSnapshot.get().values()) {
            if (runtimeOnly != isRuntimeConfig(config)) {
                next.put(storeKey(config.getNamespace(), config.getConfigKey()), config);
            }
        }
        next.putAll(nextScope);
        configSnapshot.set(immutable(next));
        log.info("Platform config {} snapshot refreshed from Nacos ({} entries)",
                runtimeOnly ? "runtime" : "platform", nextScope.size());
    }

    private Properties parseProperties(String content) {
        Properties properties = new Properties();
        if (StringUtils.isBlank(content)) {
            return properties;
        }
        try {
            properties.load(new StringReader(content));
        } catch (Exception e) {
            log.warn("Invalid Nacos platform config payload; resetting affected scope to safe defaults: {}", e.getMessage());
            return new Properties();
        }
        return properties;
    }

    private boolean containsRuntimeConfig(Properties properties) {
        for (String propertyName : properties.stringPropertyNames()) {
            if (propertyName.startsWith("system.") && propertyName.endsWith(".value")) {
                return true;
            }
        }
        return false;
    }

    private boolean containsPlatformConfig(Properties properties) {
        for (String propertyName : properties.stringPropertyNames()) {
            if (!propertyName.startsWith("system.") && propertyName.endsWith(".value")) {
                return true;
            }
        }
        return false;
    }

    private boolean publishToNacos(String content, boolean runtimeConfig) throws IOException {
        if (nacosConfigSyncService == null) {
            throw new IOException("Nacos config service is required for platform configuration writes");
        }
        try {
            boolean published = runtimeConfig
                    ? nacosConfigSyncService.publishRuntimeSwitches(content)
                    : nacosConfigSyncService.publish(content);
            if (!published) {
                throw new IOException("Nacos rejected platform configuration publish");
            }
            if (platformConfigChangeNotifier == null) {
                log.warn("Nacos config committed but Redis fan-out is unavailable; Nacos listeners remain the delivery fallback");
                return false;
            }
            boolean notified;
            if (runtimeConfig) {
                notified = platformConfigChangeNotifier.notifyRuntime(content);
            } else {
                notified = platformConfigChangeNotifier.notifyPlatform(content);
            }
            if (!notified) {
                log.warn("Nacos config committed but Redis fan-out is pending; contentHash={}", contentHash(content));
            }
            return notified;
        } catch (IllegalStateException e) {
            throw new IOException("Failed to publish config to Nacos: " + e.getMessage(), e);
        }
    }

    private void verifyExpectedContentHash(String expectedContentHash, boolean runtimeConfig) throws IOException {
        if (StringUtils.isBlank(expectedContentHash)) {
            return;
        }
        String currentContent = serializePropertiesContent(configSnapshot.get(), runtimeConfig);
        if (!expectedContentHash.equals(contentHash(currentContent))) {
            throw new IOException("配置已被其他 Admin 副本修改，请重新读取后重试");
        }
    }

    private String serializePropertiesContent(Map<String, AdminConfigResponseDTO> values, boolean runtimeOnly)
            throws IOException {
        Properties properties = new Properties();
        for (AdminConfigResponseDTO config : values.values()) {
            if (runtimeOnly != isRuntimeConfig(config)) {
                continue;
            }
            String prefix = config.getNamespace() + "." + config.getConfigKey();
            properties.setProperty(prefix + ".value", StringUtils.defaultString(config.getConfigValue()));
            properties.setProperty(prefix + ".description", StringUtils.defaultString(config.getDescription()));
        }
        // Properties.store() writes a current-time comment, which would make
        // contentHash change on every read and invalidate optimistic CAS.
        List<String> names = new ArrayList<>(properties.stringPropertyNames());
        names.sort(String::compareTo);
        StringWriter writer = new StringWriter();
        for (String name : names) {
            writer.append(escapeProperty(name, true))
                    .append('=')
                    .append(escapeProperty(properties.getProperty(name), false))
                    .append('\n');
        }
        return writer.toString();
    }

    private String escapeProperty(String value, boolean key) {
        StringBuilder escaped = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (character == '\\') {
                escaped.append("\\\\");
            } else if (character == '\n') {
                escaped.append("\\n");
            } else if (character == '\r') {
                escaped.append("\\r");
            } else if (character == '\t') {
                escaped.append("\\t");
            } else if ((i == 0 && (character == ' ' || character == '#' || character == '!'))
                    || (key && (character == '=' || character == ':'))) {
                escaped.append('\\').append(character);
            } else {
                escaped.append(character);
            }
        }
        return escaped.toString();
    }

    private Map<String, AdminConfigResponseDTO> defaultConfigs() {
        Map<String, AdminConfigResponseDTO> defaults = new LinkedHashMap<>();
        putDefault(defaults, "chatbot", "enabled", "true", "Chatbot entrance switch");
        putDefault(defaults, "chatbot", "provider", "local", "Provider: local, deepseek, openai");
        putDefault(defaults, "chatbot", "apiKey", "", "LLM provider API key");
        putDefault(defaults, "chatbot", "baseUrl", "https://api.deepseek.com", "LLM provider base URL");
        putDefault(defaults, "chatbot", "model", "deepseek-chat", "LLM model name");
        putDefault(defaults, "system", "degradeSwitch", "close", "Global raffle degrade switch");
        putDefault(defaults, "system", "rateLimiterSwitch", "close", "Global rate limiter switch");
        putDefault(defaults, "activity.100301", "state", "closed", "Legacy activity display state (fulfillment unavailable)");
        putDefault(defaults, "activity.100301", "title", "幸运轮盘活动", "Demo activity title");
        putDefault(defaults, "activity.100301", "copy", "登录参与抽奖，AI 帮你解读活动权益。", "Demo activity copy");
        putDefault(defaults, "activity.100401", "state", "online", "Staged demo activity display state");
        putDefault(defaults, "activity.100401", "title", "OpenAi抽奖活动", "Staged demo activity title");
        putDefault(defaults, "activity.100401", "copy", "登录参与抽奖，AI 帮你解读活动权益。", "Staged demo activity copy");
        return defaults;
    }

    private Map<String, AdminConfigResponseDTO> defaultsForScope(boolean runtimeOnly) {
        Map<String, AdminConfigResponseDTO> defaults = new LinkedHashMap<>();
        for (AdminConfigResponseDTO config : defaultConfigs().values()) {
            if (runtimeOnly == isRuntimeConfig(config)) {
                defaults.put(storeKey(config.getNamespace(), config.getConfigKey()), config);
            }
        }
        return defaults;
    }

    private void putDefault(Map<String, AdminConfigResponseDTO> defaults, String namespace, String key,
                            String value, String description) {
        defaults.put(storeKey(namespace, key), AdminConfigResponseDTO.builder()
                .namespace(namespace)
                .configKey(key)
                .configValue(value)
                .description(description)
                .updateTime(System.currentTimeMillis())
                .build());
    }

    private boolean isRuntimeConfig(AdminConfigResponseDTO config) {
        return config != null && "system".equals(config.getNamespace());
    }

    private Map<String, AdminConfigResponseDTO> immutable(Map<String, AdminConfigResponseDTO> values) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
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

    private AdminConfigResponseDTO withCurrentMetadata(AdminConfigResponseDTO config) {
        try {
            return config.toBuilder()
                    .contentHash(contentHash(serializePropertiesContent(configSnapshot.get(), isRuntimeConfig(config))))
                    .source("nacos")
                    .build();
        } catch (IOException e) {
            log.warn("Unable to compute current platform config hash: {}", e.getMessage());
            return config;
        }
    }

    private String storeKey(String namespace, String configKey) {
        return namespace + ":" + configKey;
    }
}

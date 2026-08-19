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
 * 仅由 Nacos 提供持久化的平台代理配置服务。
 *
 * <p>进程内 Map 只是两个 Nacos DataId 的不可变读快照，不写入本地文件。
 * 配置发布失败时不会向 Admin 报告保存成功；Nacos 持久化成功才是提交点，
 * Redis 广播只是可重试的投递提示，不能回滚已经提交的 Nacos 配置。只有删除配置，
 * 或必须解释空/非法 Nacos 内容时，才使用代码内置的安全默认值。</p>
 */
@Service
public class PlatformConfigService implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(PlatformConfigService.class);

    private final AtomicReference<Map<String, AdminConfigResponseDTO>> configSnapshot =
            new AtomicReference<>(Collections.<String, AdminConfigResponseDTO>emptyMap());

    @Autowired(required = false)
    /** Nacos 配置读写与持久化确认桥接器。 */
    private NacosConfigSyncService nacosConfigSyncService;

    @Autowired(required = false)
    /** Redis 配置变更广播器；缺失或失败时由 Nacos 监听和启动读取兜底。 */
    private PlatformConfigChangeNotifier platformConfigChangeNotifier;

    @Override
    public void afterPropertiesSet() {
        configSnapshot.set(immutable(defaultConfigs()));
        if (nacosConfigSyncService == null) {
            // 测试上下文可以不装配 Nacos；运行服务会启用桥接器，未连接时拒绝写入。
            return;
        }
        refreshPlatformFromContent(nacosConfigSyncService.fetchCurrent(3000));
        refreshRuntimeFromContent(nacosConfigSyncService.fetchRuntimeSwitches(3000));
    }

    /** 查询当前不可变配置快照，可按 namespace 过滤。 */
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

    /** 查询一个配置项，并附带当前快照的 content hash 元数据。 */
    public AdminConfigResponseDTO get(String namespace, String configKey) {
        AdminConfigResponseDTO config = configSnapshot.get().get(storeKey(namespace, configKey));
        return config == null ? null : withCurrentMetadata(config);
    }

    /** 读取配置值；删除、空值或非法快照统一回退到调用方提供的安全默认值。 */
    public String getValue(String namespace, String configKey, String defaultValue) {
        AdminConfigResponseDTO config = get(namespace, configKey);
        if (config != null && "__DELETED__".equals(config.getDescription())) {
            return defaultValue;
        }
        return config == null || StringUtils.isBlank(config.getConfigValue())
                ? defaultValue : config.getConfigValue();
    }

    /** 返回一个不可变的配置代快照，供同一请求范围内保持一致地读取配置。 */
    public Map<String, String> snapshotValues(String namespace) {
        Map<String, String> values = new LinkedHashMap<>();
        for (AdminConfigResponseDTO config : configSnapshot.get().values()) {
            if (namespace.equals(config.getNamespace()) && !"__DELETED__".equals(config.getDescription())) {
                values.put(config.getConfigKey(), config.getConfigValue());
            }
        }
        return Collections.unmodifiableMap(values);
    }

    /**
     * 保存配置：校验期望 hash → 发布 Nacos → 更新本地快照 → 尝试 Redis fan-out。
     * Nacos publish 是提交点，通知失败只返回 pending，不回滚已提交配置。
     */
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

    /** 以 tombstone 形式删除一个配置项，并复用 save 的乐观并发约束。 */
    public synchronized void delete(String namespace, String configKey) throws IOException {
        AdminConfigRequestDTO request = new AdminConfigRequestDTO();
        request.setNamespace(namespace);
        request.setConfigKey(configKey);
        delete(request);
    }

    /** 按与保存相同的乐观并发约束删除配置键，并以 tombstone 记录删除事实。 */
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

    /** 根据一个 Nacos 配置内容整体替换非运行时配置快照。 */
    public void refreshPlatformFromContent(String content) {
        replaceScope(content, false);
    }

    /** 根据一个 Nacos 配置内容整体替换运行时开关配置快照。 */
    public void refreshRuntimeFromContent(String content) {
        replaceScope(content, true);
    }

    /** 兼容一次只接收一个 DataId 内容的调用方，并按键名识别其配置范围。 */
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
                // 删除会恢复该范围的安全默认值，不能继续保留旧的内存配置。
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
        // Properties.store() 会写入当前时间注释，导致每次读取都改变 contentHash，
        // 从而使乐观 CAS 失效，因此这里按稳定顺序手工序列化。
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

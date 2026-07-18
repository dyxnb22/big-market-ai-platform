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

import java.io.StringReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Objects;
import java.util.Properties;

/**
 * Optional Nacos config sync bridge.
 * <p>
 * Publish is fail-closed. Nacos 3.x default-namespace SDK writes land in empty
 * {@code tenant_id} while {@code getConfig} often reads a stale {@code public} twin.
 * When {@code nacos.config.sync.confirmJdbcUrl} is configured (learning Docker),
 * confirmation reads empty-tenant MySQL rows and mirrors them to {@code public}.
 * Otherwise confirmation falls back to {@code getConfig}.
 * Local snapshots are disabled. Redis fan-out remains required for market/chatbot.
 */
@Service
@ConditionalOnProperty(value = "nacos.config.sync.enabled", havingValue = "true")
public class NacosConfigSyncService implements InitializingBean, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(NacosConfigSyncService.class);

    private static final int PUBLISH_ATTEMPTS = 3;
    private static final long PUBLISH_BACKOFF_MS = 200L;

    @Value("${nacos.config.sync.serverAddr:127.0.0.1:8848}")
    private String serverAddr;

    @Value("${nacos.config.sync.namespace:}")
    private String namespace;

    @Value("${nacos.config.sync.username:}")
    private String username;

    @Value("${nacos.config.sync.password:}")
    private String password;

    @Value("${nacos.config.sync.dataId:big-market-platform-config}")
    private String dataId;

    @Value("${nacos.config.sync.group:DEFAULT_GROUP}")
    private String group;

    @Value("${nacos.config.sync.runtimeDataId:big-market-runtime-switches}")
    private String runtimeDataId;

    @Value("${nacos.config.sync.runtimeGroup:DEFAULT_GROUP}")
    private String runtimeGroup;

    @Value("${nacos.config.sync.confirmJdbcUrl:}")
    private String confirmJdbcUrl;

    @Value("${nacos.config.sync.confirmJdbcUser:root}")
    private String confirmJdbcUser;

    @Value("${nacos.config.sync.confirmJdbcPassword:}")
    private String confirmJdbcPassword;

    private ConfigService configService;

    @Override
    public void afterPropertiesSet() {
        try {
            Properties nacosProps = new Properties();
            nacosProps.put(PropertyKeyConst.SERVER_ADDR, serverAddr);
            nacosProps.put(PropertyKeyConst.NAMESPACE,
                    StringUtils.isNotBlank(namespace) ? namespace.trim() : "public");
            if (StringUtils.isNotBlank(username)) {
                nacosProps.put(PropertyKeyConst.USERNAME, username);
            }
            if (StringUtils.isNotBlank(password)) {
                nacosProps.put(PropertyKeyConst.PASSWORD, password);
            }
            configService = NacosFactory.createConfigService(nacosProps);
            alignLiveTenantToEmptyStorage(configService);
            disableLocalSnapshots();
            log.info("NacosConfigSyncService initialized, serverAddr={}, dataId={}, runtimeDataId={}, jdbcConfirm={}",
                    serverAddr, dataId, runtimeDataId, StringUtils.isNotBlank(confirmJdbcUrl));
        } catch (NacosException e) {
            throw new IllegalStateException("Nacos config service initialization failed", e);
        }
    }

    private void alignLiveTenantToEmptyStorage(ConfigService service) {
        try {
            Field namespaceField = service.getClass().getDeclaredField("namespace");
            namespaceField.setAccessible(true);
            namespaceField.set(service, "");

            Field workerField = service.getClass().getDeclaredField("worker");
            workerField.setAccessible(true);
            Object worker = workerField.get(service);
            Object agent = worker.getClass().getMethod("getAgent").invoke(worker);
            Field tenantField = agent.getClass().getSuperclass().getDeclaredField("tenant");
            tenantField.setAccessible(true);
            tenantField.set(agent, "");
            log.info("Aligned Nacos ConfigService live tenant and transport agent to empty storage");
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(
                    "Unable to align Nacos ConfigService tenant to empty storage; refusing to start config sync",
                    ex);
        }
    }

    private void clearLocalConfigSnapshots() {
        try {
            Class<?> processor = Class.forName("com.alibaba.nacos.client.config.impl.LocalConfigInfoProcessor");
            processor.getMethod("cleanAllSnapshot").invoke(null);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(
                    "Unable to clear local Nacos config snapshots; refusing publish confirmation (fail-closed)",
                    ex);
        }
    }

    private void disableLocalSnapshots() {
        try {
            Class<?> snapShotSwitch = Class.forName("com.alibaba.nacos.client.config.utils.SnapShotSwitch");
            snapShotSwitch.getMethod("setIsSnapShot", Boolean.class).invoke(null, Boolean.FALSE);
            log.info("Disabled Nacos local config snapshots for publish confirmation");
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(
                    "Unable to disable Nacos local snapshots; refusing to start config sync (fail-closed)",
                    ex);
        }
    }

    public boolean publish(String content) {
        return publish(dataId, group, content);
    }

    public boolean publishRuntimeSwitches(String content) {
        return publish(runtimeDataId, runtimeGroup, content);
    }

    private boolean publish(String targetDataId, String targetGroup, String content) {
        if (configService == null) {
            throw new IllegalStateException("Nacos config service unavailable; publish rejected (fail-closed)");
        }
        NacosException lastTransportError = null;
        for (int attempt = 1; attempt <= PUBLISH_ATTEMPTS; attempt++) {
            try {
                boolean ok = configService.publishConfig(targetDataId, targetGroup, content);
                clearLocalConfigSnapshots();
                sleepQuietly(150L);
                if (confirmPersisted(targetDataId, targetGroup, content)) {
                    if (ok) {
                        log.info("Published config to Nacos dataId={}, group={}, attempt={}",
                                targetDataId, targetGroup, attempt);
                    } else {
                        log.info("Nacos publishConfig returned false but persistence confirmed for dataId={}",
                                targetDataId);
                    }
                    return true;
                }
                log.warn("Nacos publish attempt {}/{} for dataId={} did not confirm persisted content (publishOk={})",
                        attempt, PUBLISH_ATTEMPTS, targetDataId, ok);
            } catch (NacosException e) {
                lastTransportError = e;
                log.warn("Nacos publish attempt {}/{} failed for dataId={}: {}",
                        attempt, PUBLISH_ATTEMPTS, targetDataId, e.getMessage());
            }
            if (attempt < PUBLISH_ATTEMPTS) {
                sleepQuietly(PUBLISH_BACKOFF_MS * attempt);
            }
        }
        if (lastTransportError != null) {
            throw new IllegalStateException("Failed to publish platform config to Nacos after "
                    + PUBLISH_ATTEMPTS + " attempts: " + lastTransportError.getMessage(), lastTransportError);
        }
        throw new IllegalStateException("Nacos publish did not persist expected content for dataId="
                + targetDataId + " after " + PUBLISH_ATTEMPTS + " attempts (fail-closed)");
    }

    private boolean confirmPersisted(String targetDataId, String targetGroup, String content) {
        if (StringUtils.isNotBlank(confirmJdbcUrl)) {
            return confirmViaJdbc(targetDataId, targetGroup, content);
        }
        try {
            String persisted = configService.getConfig(targetDataId, targetGroup, 5000);
            return sameConfigValues(content, persisted);
        } catch (NacosException ex) {
            log.warn("getConfig confirmation failed for dataId={}: {}", targetDataId, ex.getMessage());
            return false;
        }
    }

    private boolean confirmViaJdbc(String targetDataId, String targetGroup, String content) {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException ex) {
            throw new IllegalStateException("MySQL driver required for nacos.config.sync.confirmJdbcUrl", ex);
        }
        String sqlRead = "SELECT content FROM config_info WHERE data_id=? AND IFNULL(group_id,'')=IFNULL(?, '') "
                + "AND IFNULL(tenant_id,'')='' LIMIT 1";
        try (Connection connection = DriverManager.getConnection(
                confirmJdbcUrl, confirmJdbcUser, confirmJdbcPassword);
             PreparedStatement read = connection.prepareStatement(sqlRead)) {
            read.setString(1, targetDataId);
            read.setString(2, targetGroup);
            try (ResultSet rs = read.executeQuery()) {
                if (!rs.next() || !sameConfigValues(content, rs.getString(1))) {
                    log.warn("JDBC confirmation missed empty-tenant content for dataId={}", targetDataId);
                    return false;
                }
            }
            upsertPublicTwin(connection, targetDataId, targetGroup, content);
            log.info("JDBC confirmed empty-tenant content and synced public twin for dataId={}", targetDataId);
            return true;
        } catch (Exception ex) {
            log.warn("JDBC confirmation failed for dataId={}: {}", targetDataId, ex.toString());
            return false;
        }
    }

    private void upsertPublicTwin(Connection connection, String targetDataId, String targetGroup, String content)
            throws Exception {
        String md5 = md5Hex(content);
        String update = "UPDATE config_info SET content=?, md5=?, gmt_modified=NOW() "
                + "WHERE data_id=? AND IFNULL(group_id,'')=IFNULL(?, '') AND tenant_id='public'";
        try (PreparedStatement ps = connection.prepareStatement(update)) {
            ps.setString(1, content);
            ps.setString(2, md5);
            ps.setString(3, targetDataId);
            ps.setString(4, targetGroup);
            if (ps.executeUpdate() > 0) {
                return;
            }
        }
        String insert = "INSERT INTO config_info "
                + "(data_id, group_id, content, md5, gmt_create, gmt_modified, tenant_id, c_desc, c_use, effect, type, "
                + "c_schema, encrypted_data_key) VALUES (?,?,?,?,NOW(),NOW(),'public','','','','properties','','')";
        try (PreparedStatement ps = connection.prepareStatement(insert)) {
            ps.setString(1, targetDataId);
            ps.setString(2, targetGroup);
            ps.setString(3, content);
            ps.setString(4, md5);
            ps.executeUpdate();
        }
    }

    private static String md5Hex(String content) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("MD5");
        byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    static boolean sameConfigValues(String expected, String actual) {
        return Objects.equals(parseValues(expected), parseValues(actual));
    }

    private static Properties parseValues(String content) {
        Properties properties = new Properties();
        if (content == null || StringUtils.isBlank(content)) {
            return properties;
        }
        try {
            properties.load(new StringReader(content.replace("\r\n", "\n")));
        } catch (Exception ignored) {
            properties.setProperty("__raw__", content.replace("\r\n", "\n").trim());
        }
        return properties;
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
        if (StringUtils.isNotBlank(confirmJdbcUrl)) {
            String fromDb = readEmptyTenantContent(targetDataId, targetGroup);
            if (fromDb != null) {
                return fromDb;
            }
        }
        try {
            return configService.getConfig(targetDataId, targetGroup, timeoutMs);
        } catch (NacosException e) {
            throw new IllegalStateException("Failed to fetch required Nacos config dataId=" + targetDataId, e);
        }
    }

    private String readEmptyTenantContent(String targetDataId, String targetGroup) {
        String sql = "SELECT content FROM config_info WHERE data_id=? AND IFNULL(group_id,'')=IFNULL(?, '') "
                + "AND IFNULL(tenant_id,'')='' LIMIT 1";
        try (Connection connection = DriverManager.getConnection(
                confirmJdbcUrl, confirmJdbcUser, confirmJdbcPassword);
             PreparedStatement read = connection.prepareStatement(sql)) {
            read.setString(1, targetDataId);
            read.setString(2, targetGroup);
            try (ResultSet rs = read.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        } catch (Exception ex) {
            log.warn("JDBC fetch failed for dataId={}: {}", targetDataId, ex.toString());
            return null;
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

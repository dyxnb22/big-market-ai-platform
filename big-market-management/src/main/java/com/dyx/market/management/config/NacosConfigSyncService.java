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
 * 可选的 Nacos 配置同步桥接器。
 *
 * <p>配置发布采用失败即关闭策略。Nacos 3.x 默认命名空间的 SDK 写入通常落在空
 * {@code tenant_id}，而 {@code getConfig} 可能读取到过期的 {@code public} 副本。
 * 配置 {@code nacos.config.sync.confirmJdbcUrl} 后（学习环境 Docker 使用该方式），
 * 发布确认会读取空租户的 MySQL 行，并同步一份 {@code public} 副本；未配置时退回
 * {@code getConfig} 确认。这里禁用本地快照，Redis 广播只作为尽力加速通道，Nacos
 * 监听器和启动读取仍是可持久依赖的投递兜底。</p>
 */
@Service
@ConditionalOnProperty(value = "nacos.config.sync.enabled", havingValue = "true")
public class NacosConfigSyncService implements InitializingBean, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(NacosConfigSyncService.class);

    private static final int PUBLISH_ATTEMPTS = 3;
    private static final long PUBLISH_BACKOFF_MS = 200L;

    @Value("${nacos.config.sync.serverAddr:127.0.0.1:8848}")
    /** Nacos 服务地址。 */
    private String serverAddr;

    @Value("${nacos.config.sync.namespace:}")
    /** Nacos 命名空间；本桥接器只支持空值或 public。 */
    private String namespace;

    @Value("${nacos.config.sync.username:}")
    /** Nacos 登录用户名。 */
    private String username;

    @Value("${nacos.config.sync.password:}")
    /** Nacos 登录密码。 */
    private String password;

    @Value("${nacos.config.sync.dataId:big-market-platform-config}")
    /** 平台配置使用的 Nacos DataId。 */
    private String dataId;

    @Value("${nacos.config.sync.group:DEFAULT_GROUP}")
    /** 平台配置使用的 Nacos 分组。 */
    private String group;

    @Value("${nacos.config.sync.runtimeDataId:big-market-runtime-switches}")
    /** 运行时开关使用的 Nacos DataId。 */
    private String runtimeDataId;

    @Value("${nacos.config.sync.runtimeGroup:DEFAULT_GROUP}")
    /** 运行时开关使用的 Nacos 分组。 */
    private String runtimeGroup;

    @Value("${nacos.config.sync.confirmJdbcUrl:}")
    /** 用于确认空租户持久化内容的 MySQL JDBC 地址；为空时使用 Nacos API 确认。 */
    private String confirmJdbcUrl;

    @Value("${nacos.config.sync.confirmJdbcUser:root}")
    /** 确认 MySQL 的用户名。 */
    private String confirmJdbcUser;

    @Value("${nacos.config.sync.confirmJdbcPassword:}")
    /** 确认 MySQL 的密码。 */
    private String confirmJdbcPassword;

    /** 已创建的 Nacos 配置客户端。 */
    private ConfigService configService;

    @Override
    /** 初始化 Nacos 客户端，校验命名空间并关闭可能污染发布确认的本地快照。 */
    public void afterPropertiesSet() {
        try {
            validateNamespace();
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

    private void validateNamespace() {
        if (!isSupportedNamespace(namespace)) {
            throw new IllegalStateException("Only the public Nacos namespace is supported by the empty-tenant sync bridge; "
                    + "configure nacos.config.sync.namespace=public or leave it blank");
        }
    }

    static boolean isSupportedNamespace(String configuredNamespace) {
        return StringUtils.isBlank(configuredNamespace)
                || "public".equalsIgnoreCase(configuredNamespace.trim());
    }

    /** 将 SDK 的运行时租户调整为空租户，使读写目标与 Nacos 数据库存储一致。 */
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

    /** 清理 Nacos 客户端本地快照，防止发布确认读到旧文件。 */
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

    /** 关闭 Nacos 本地快照；无法关闭时拒绝启动，确保确认逻辑不会被旧快照绕过。 */
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

    /** 发布平台配置，并在 Nacos 持久化确认成功后返回成功。 */
    public boolean publish(String content) {
        return publish(dataId, group, content);
    }

    /** 发布运行时开关配置，并在 Nacos 持久化确认成功后返回成功。 */
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

    /** 通过空租户 MySQL 行确认发布内容，并同步 public 副本供兼容读取。 */
    private boolean confirmViaJdbc(String targetDataId, String targetGroup, String content) {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException ex) {
            throw new IllegalStateException("MySQL driver required for nacos.config.sync.confirmJdbcUrl", ex);
        }
        String sqlRead = "SELECT content FROM config_info WHERE data_id=? AND IFNULL(group_id,'')=IFNULL(?, '') "
                + "AND IFNULL(tenant_id,'')='' LIMIT 1";
        try (Connection connection = DriverManager.getConnection(
                confirmJdbcUrl, confirmJdbcUser, confirmJdbcPassword)) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (PreparedStatement read = connection.prepareStatement(sqlRead + " FOR UPDATE")) {
                read.setString(1, targetDataId);
                read.setString(2, targetGroup);
                try (ResultSet rs = read.executeQuery()) {
                    if (!rs.next() || !sameConfigValues(content, rs.getString(1))) {
                        connection.rollback();
                        log.warn("JDBC confirmation missed empty-tenant content for dataId={}", targetDataId);
                        return false;
                    }
                }
                upsertPublicTwin(connection, targetDataId, targetGroup, content);
                connection.commit();
                log.info("JDBC confirmed empty-tenant content and synced public twin for dataId={}", targetDataId);
                return true;
            } catch (Exception ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        } catch (Exception ex) {
            log.warn("JDBC confirmation failed for dataId={}: {}", targetDataId, ex.toString());
            return false;
        }
    }

    /** 在同一事务中写入或更新 public 租户的兼容副本。 */
    void upsertPublicTwin(Connection connection, String targetDataId, String targetGroup, String content)
            throws Exception {
        String md5 = md5Hex(content);
        String upsert = "INSERT INTO config_info "
                + "(data_id, group_id, content, md5, gmt_create, gmt_modified, tenant_id, c_desc, c_use, effect, type, "
                + "c_schema, encrypted_data_key) VALUES (?,?,?,?,NOW(),NOW(),'public','','','','properties','','') "
                + "ON DUPLICATE KEY UPDATE content=VALUES(content), md5=VALUES(md5), gmt_modified=NOW()";
        try (PreparedStatement ps = connection.prepareStatement(upsert)) {
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

    /** 读取平台配置；配置未启用或读取失败时直接抛出，避免使用不确定快照启动。 */
    public String fetchCurrent(long timeoutMs) {
        return fetchCurrent(dataId, group, timeoutMs);
    }

    /** 读取运行时开关配置；优先读取空租户数据库内容。 */
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

    /** 注册平台配置监听器。 */
    public void addListener(Listener listener) {
        addListener(dataId, group, listener);
    }

    /** 注册运行时开关配置监听器。 */
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

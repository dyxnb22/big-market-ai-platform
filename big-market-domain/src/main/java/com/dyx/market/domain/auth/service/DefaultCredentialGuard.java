package com.dyx.market.domain.auth.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 启动时凭据守卫：非 dev 环境若仍使用已知默认密钥/口令则拒绝启动。
 * <p>
 * dev、local、docker 配置档除外，便于本地开发与 docker-compose 无需注入外部密钥即可运行。
 * 置于 big-market-domain，供扫描 {@code com.dyx.market.domain.auth} 的各服务自动发现。
 * <p>
 * 只读检查，不修改配置。可通过 {@code DEFAULT_CREDENTIAL_GUARD_ENABLED=false} 关闭。
 */
@Slf4j
@Component
public class DefaultCredentialGuard implements InitializingBean {

    private static final List<String> DEV_PROFILES = Arrays.asList("dev", "local", "docker");

    private static final Set<String> DANGEROUS_PATTERNS = new LinkedHashSet<>(Arrays.asList(
            "change-me",
            "change-me-in-dev-only",
            "change-me-in-test-only",
            "change-me-in-prod",
            "default_token",
            "6ec604541f8b1ce4a",
            "89iu7o8732ijd9114",
            "xiaofuge:demo,admin:admin",
            "admin:admin"
    ));

    @Value("${default-credential-guard.enabled:true}")
    private boolean guardEnabled;

    private final Environment environment;
    private final String jwtSecret;
    private final String devUsers;
    private final String adminToken;

    public DefaultCredentialGuard(Environment environment,
                                   @Value("${app.jwt.secret:}") String jwtSecret,
                                   @Value("${app.auth.dev-users:}") String devUsers,
                                   @Value("${app.admin.token:}") String adminToken) {
        this.environment = environment;
        this.jwtSecret = jwtSecret;
        this.devUsers = devUsers;
        this.adminToken = adminToken;
    }

    @Override
    public void afterPropertiesSet() {
        if (!guardEnabled) {
            log.warn("[DefaultCredentialGuard] DISABLED — dangerous defaults will not be caught");
            return;
        }

        String[] activeProfiles = environment.getActiveProfiles();
        boolean isDev = activeProfiles.length == 0
                || Arrays.stream(activeProfiles).anyMatch(DEV_PROFILES::contains);

        if (isDev) {
            log.info("[DefaultCredentialGuard] dev/local/docker profile — allowing default credentials for development");
            return;
        }

        Set<String> violations = new LinkedHashSet<>();

        check("app.jwt.secret", jwtSecret, violations);
        check("app.auth.dev-users", devUsers, violations);
        check("app.admin.token", adminToken, violations);

        if (!violations.isEmpty()) {
            String msg = "\n==========================================================\n"
                    + "[DefaultCredentialGuard] REFUSING TO START — dangerous default "
                    + "credentials detected in non-dev profile: "
                    + String.join(", ", activeProfiles) + "\n"
                    + "The following properties still carry well-known defaults:\n"
                    + String.join("\n", violations)
                    + "\n\nOverride them via environment variables before deploying to "
                    + "staging or production.\n"
                    + "To suppress this guard in dev-only scenarios, set "
                    + "DEFAULT_CREDENTIAL_GUARD_ENABLED=false\n"
                    + "==========================================================";
            log.error(msg);
            throw new IllegalStateException(msg);
        }

        log.info("[DefaultCredentialGuard] all checked credentials are non-default");
    }

    private void check(String propertyName, String value, Set<String> violations) {
        if (value == null || value.trim().isEmpty()) return;
        for (String pattern : DANGEROUS_PATTERNS) {
            if (value.equalsIgnoreCase(pattern) || value.contains(pattern)) {
                violations.add("  - " + propertyName + " contains default: '" + pattern + "'");
                break;
            }
        }
    }
}

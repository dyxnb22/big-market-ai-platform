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
 * {@code secure} 优先级高于 {@code docker}/{@code local}/{@code dev}：
 * 只要激活了 secure，即使同时存在 docker 也不得放行默认凭据。
 */
@Slf4j
@Component
public class DefaultCredentialGuard implements InitializingBean {

    private static final List<String> DEV_PROFILES = Arrays.asList("dev", "local", "docker");
    private static final String SECURE_PROFILE = "secure";

    private static final Set<String> DANGEROUS_PATTERNS = new LinkedHashSet<>(Arrays.asList(
            "change-me",
            "change-me-in-dev-only",
            "change-me-in-test-only",
            "change-me-in-prod",
            "change-me-in-docker-dev-only",
            "change-me-chat-internal",
            "change-me-chat-internal-docker",
            "default_token",
            "big-market-internal-dev",
            "6ec604541f8b1ce4a",
            "89iu7o8732ijd9114",
            "xiaofuge:demo",
            "xiaofuge:demo,admin:admin",
            "admin:admin",
            "admin-dev-token"
    ));

    @Value("${default-credential-guard.enabled:true}")
    private boolean guardEnabled;

    private final Environment environment;
    private final String jwtSecret;
    private final String devUsers;
    private final String adminToken;
    private final String internalRpcToken;
    private final String chatInternalToken;
    private final String xxlAccessToken;
    private final String gatewayAppToken;

    public DefaultCredentialGuard(Environment environment,
                                   @Value("${app.jwt.secret:}") String jwtSecret,
                                   @Value("${app.auth.dev-users:}") String devUsers,
                                   @Value("${app.admin.token:}") String adminToken,
                                   @Value("${app.internal-rpc.token:}") String internalRpcToken,
                                   @Value("${chat.internal-service-token:}") String chatInternalToken,
                                   @Value("${xxl.job.accessToken:}") String xxlAccessToken,
                                   @Value("${gateway.config.big-market-appToken:}") String gatewayAppToken) {
        this.environment = environment;
        this.jwtSecret = jwtSecret;
        this.devUsers = devUsers;
        this.adminToken = adminToken;
        this.internalRpcToken = internalRpcToken;
        this.chatInternalToken = chatInternalToken;
        this.xxlAccessToken = xxlAccessToken;
        this.gatewayAppToken = gatewayAppToken;
    }

    @Override
    public void afterPropertiesSet() {
        if (!guardEnabled) {
            log.warn("[DefaultCredentialGuard] DISABLED — dangerous defaults will not be caught");
            return;
        }

        String[] activeProfiles = environment.getActiveProfiles();
        boolean secureActive = Arrays.stream(activeProfiles).anyMatch(SECURE_PROFILE::equalsIgnoreCase);
        boolean isDev = !secureActive && (activeProfiles.length == 0
                || Arrays.stream(activeProfiles).anyMatch(DEV_PROFILES::contains));

        if (isDev) {
            log.info("[DefaultCredentialGuard] dev/local/docker profile — allowing default credentials for development");
            return;
        }

        if (secureActive) {
            log.info("[DefaultCredentialGuard] secure profile active — enforcing non-default credentials (docker bypass disabled)");
        }

        Set<String> violations = new LinkedHashSet<>();

        check("app.jwt.secret", jwtSecret, violations);
        check("app.auth.dev-users", devUsers, violations);
        check("app.admin.token", adminToken, violations);
        check("app.internal-rpc.token", internalRpcToken, violations);
        check("chat.internal-service-token", chatInternalToken, violations);
        check("xxl.job.accessToken", xxlAccessToken, violations);
        check("gateway.config.big-market-appToken", gatewayAppToken, violations);

        if (secureActive) {
            requireNonBlank("app.jwt.secret", jwtSecret, violations);
            requireNonBlank("app.internal-rpc.token", internalRpcToken, violations);
        }

        if (!violations.isEmpty()) {
            String msg = "\n==========================================================\n"
                    + "[DefaultCredentialGuard] REFUSING TO START — dangerous default "
                    + "credentials detected in non-dev profile: "
                    + String.join(", ", activeProfiles) + "\n"
                    + "The following properties still carry well-known defaults or are missing:\n"
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

    private void requireNonBlank(String propertyName, String value, Set<String> violations) {
        if (value == null || value.trim().isEmpty()) {
            violations.add("  - " + propertyName + " is blank (required under secure profile)");
        }
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

package com.dyx.market.auth.service.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Fail-fast startup guard that refuses to boot when a non-dev profile is
 * active and any well-known default credential/secret is still in place.
 *
 * Dev profiles (dev, local, docker) are deliberately excluded so local
 * development and docker-compose stacks remain runnable without external
 * secret injection.
 *
 * SAFETY: This is a read-only startup check. It never modifies config.
 * ROLLBACK: Set DEFAULT_CREDENTIAL_GUARD_ENABLED=false to disable.
 */
@Slf4j
@Component
public class DefaultCredentialGuard implements CommandLineRunner {

    private static final List<String> DEV_PROFILES = List.of("dev", "local", "docker");

    private static final Set<String> DANGEROUS_PATTERNS = new LinkedHashSet<>(Arrays.asList(
            "change-me",
            "change-me-in-dev-only",
            "change-me-in-test-only",
            "change-me-in-prod",
            "default_token",
            "6ec604541f8b1ce4a",
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
    public void run(String... args) {
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
        if (value == null || value.isBlank()) return;
        for (String pattern : DANGEROUS_PATTERNS) {
            if (value.equalsIgnoreCase(pattern) || value.contains(pattern)) {
                violations.add("  - " + propertyName + " contains default: '" + pattern + "'");
                break;
            }
        }
    }
}

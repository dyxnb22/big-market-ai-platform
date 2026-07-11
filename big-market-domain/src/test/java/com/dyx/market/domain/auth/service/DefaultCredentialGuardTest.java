package com.dyx.market.domain.auth.service;

import org.junit.Test;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.Assert.fail;

public class DefaultCredentialGuardTest {

    @Test
    public void dockerAloneAllowsDefaults() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("docker");
        DefaultCredentialGuard guard = newGuard(env,
                "change-me-in-docker-dev-only",
                "xiaofuge:demo,admin:admin",
                "admin-dev-token",
                "big-market-internal-dev",
                "change-me-chat-internal-docker",
                "default_token",
                "");
        guard.afterPropertiesSet();
    }

    @Test(expected = IllegalStateException.class)
    public void dockerPlusSecureRejectsDefaults() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("docker", "secure");
        DefaultCredentialGuard guard = newGuard(env,
                "change-me-in-docker-dev-only",
                "xiaofuge:demo,admin:admin",
                "admin-dev-token",
                "big-market-internal-dev",
                "change-me-chat-internal-docker",
                "default_token",
                "");
        guard.afterPropertiesSet();
    }

    @Test
    public void secureWithStrongSecretsPasses() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("docker", "secure");
        DefaultCredentialGuard guard = newGuard(env,
                "strong-jwt-secret-value-32chars!!",
                "alice:s3cretPass",
                "strong-admin-token-xyz",
                "strong-rpc-token-xyz",
                "strong-chat-internal-token",
                "strong-xxl-token",
                "strong-gateway-token");
        guard.afterPropertiesSet();
    }

    @Test(expected = IllegalStateException.class)
    public void secureRejectsStandaloneDemoUser() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("docker", "secure");
        DefaultCredentialGuard guard = newGuard(env,
                "strong-jwt-secret-value-32chars!!",
                "xiaofuge:demo",
                "strong-admin-token-xyz",
                "strong-rpc-token-xyz",
                "strong-chat-internal-token",
                "strong-xxl-token",
                "strong-gateway-token");
        guard.afterPropertiesSet();
    }

    private static DefaultCredentialGuard newGuard(Environment env,
                                                   String jwt,
                                                   String users,
                                                   String admin,
                                                   String rpc,
                                                   String chat,
                                                   String xxl,
                                                   String gateway) {
        DefaultCredentialGuard guard = new DefaultCredentialGuard(env, jwt, users, admin, rpc, chat, xxl, gateway);
        try {
            java.lang.reflect.Field f = DefaultCredentialGuard.class.getDeclaredField("guardEnabled");
            f.setAccessible(true);
            f.setBoolean(guard, true);
        } catch (Exception e) {
            fail(e.getMessage());
        }
        return guard;
    }
}

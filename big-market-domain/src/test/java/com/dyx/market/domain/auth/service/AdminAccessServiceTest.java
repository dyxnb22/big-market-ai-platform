package com.dyx.market.domain.auth.service;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AdminAccessServiceTest {

    private final AdminAccessService adminAccessService = new AdminAccessService(
            new AuthService("test-secret-32-bytes-minimum!!"), "admin-dev-token", "admin");

    @Test
    public void should_accept_admin_token_header() {
        assertTrue(adminAccessService.hasAdminAccess("admin-dev-token", null));
    }

    @Test
    public void should_reject_invalid_admin_token() {
        assertFalse(adminAccessService.hasAdminAccess("wrong-token", null));
    }

    @Test
    public void should_identify_admin_user() {
        assertTrue(adminAccessService.isAdminUser("admin"));
        assertFalse(adminAccessService.isAdminUser("xiaofuge"));
    }
}

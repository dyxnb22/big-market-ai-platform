package com.dyx.market.domain.auth.service;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AbstractAuthServiceTest {

    private final AuthService authService = new AuthService("change-me-in-dev-only");

    @Test
    public void should_issue_and_verify_token() {
        String token = authService.createToken("xiaofuge");
        assertTrue(authService.checkToken(token));
        assertEquals("xiaofuge", authService.openid(token));
    }

    @Test
    public void should_extract_jti_and_expiration() {
        String token = authService.createToken("admin");
        assertFalse(authService.extractJti(token).isEmpty());
        assertTrue(authService.extractExpiration(token) > System.currentTimeMillis());
    }

    @Test
    public void should_reject_tampered_token() {
        String token = authService.createToken("xiaofuge");
        assertFalse(authService.checkToken(token + "tampered"));
    }
}

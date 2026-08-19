package com.dyx.market.domain.auth.service;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * InMemoryTokenRevocationService 单元测试，校验 Token 吊销的基本生命周期。
 */
public class TokenRevocationServiceTest {

    @Test
    public void testRevokeAndIsRevoked() {
        ITokenRevocationService svc = new InMemoryTokenRevocationService();

        String jti1 = "test-jti-001";
        String jti2 = "test-jti-002";
        long futureExpiry = System.currentTimeMillis() + 60_000L;

        assertFalse("jti should not be revoked initially", svc.isRevoked(jti1));

        svc.revoke(jti1, futureExpiry);
        assertTrue("jti should be revoked after revoke()", svc.isRevoked(jti1));
        assertFalse("other jti should not be affected", svc.isRevoked(jti2));
        assertEquals("size should be 1", 1, svc.size());
    }

    @Test
    public void testIdempotentRevoke() {
        ITokenRevocationService svc = new InMemoryTokenRevocationService();

        String jti = "test-jti-idempotent";
        long futureExpiry = System.currentTimeMillis() + 60_000L;

        svc.revoke(jti, futureExpiry);
        svc.revoke(jti, futureExpiry);
        assertTrue("jti should still be revoked after duplicate revoke()", svc.isRevoked(jti));
    }

    @Test
    public void testExpiredTokenNotRevoked() {
        ITokenRevocationService svc = new InMemoryTokenRevocationService();

        String jti = "test-jti-expired";
        long pastExpiry = System.currentTimeMillis() - 1_000L;

        svc.revoke(jti, pastExpiry);
        assertFalse("expired token should not be considered revoked", svc.isRevoked(jti));
    }

    @Test
    public void testMultipleRevocations() {
        ITokenRevocationService svc = new InMemoryTokenRevocationService();

        long future = System.currentTimeMillis() + 60_000L;
        for (int i = 0; i < 10; i++) {
            svc.revoke("jti-" + i, future);
        }

        assertEquals("size should be 10", 10, svc.size());
        for (int i = 0; i < 10; i++) {
            assertTrue("jti-" + i + " should be revoked", svc.isRevoked("jti-" + i));
        }
    }

    @Test
    public void testSizeDecreasesAfterExpiry() {
        ITokenRevocationService svc = new InMemoryTokenRevocationService();

        String jti = "test-jti-size-decrease";
        long pastExpiry = System.currentTimeMillis() - 1_000L;

        svc.revoke(jti, pastExpiry);
        svc.isRevoked(jti); // 访问时触发过期条目淘汰。
        assertEquals("size should be 0 after expired entry evicted", 0, svc.size());
    }
}

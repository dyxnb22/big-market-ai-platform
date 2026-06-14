package com.dyx.market.domain.auth.service;

/**
 * Minimal token revocation contract. Implementations may use Redis for shared
 * deployments or in-memory storage for local development.
 */
public interface ITokenRevocationService {

    /** Revoke a token by its jti. Idempotent — repeated calls are safe. */
    void revoke(String jti, long expiresAtMillis);

    /** Check whether a jti has been revoked. */
    boolean isRevoked(String jti);

    /** Approximate count of currently tracked revoked tokens. For monitoring. */
    long size();
}

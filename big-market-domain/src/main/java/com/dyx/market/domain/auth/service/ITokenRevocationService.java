package com.dyx.market.domain.auth.service;

/**
 * Token 吊销契约：生产环境可用 Redis 共享，本地开发可用内存实现。
 */
public interface ITokenRevocationService {

    /** 按 jti 吊销 Token，幂等，重复调用安全。 */
    void revoke(String jti, long expiresAtMillis);

    boolean isRevoked(String jti);

    /** 当前跟踪的已吊销 Token 近似数量，供监控使用。 */
    long size();
}

package com.dyx.market.domain.auth.service;

/**
 * @author Fuzhengwei bugstack.cn @小傅哥
 * @description 认证
 * @create 2024-10-07 17:54
 */
public interface IAuthService {

    String createToken(String openid);

    boolean checkToken(String token);

    String openid(String token);

    /**
     * Extract the jti (JWT ID) claim from a token. Returns null if the token
     * is invalid or the jti claim is absent.
     */
    String extractJti(String token);

    /**
     * Extract the expiration time from a token in epoch millis.
     * Returns 0 if the token is invalid or has no expiration.
     */
    long extractExpiration(String token);
}

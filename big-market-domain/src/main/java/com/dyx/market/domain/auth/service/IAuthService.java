package com.dyx.market.domain.auth.service;

/**
 * 认证服务契约：Token 签发、校验与用户标识解析。
 */
public interface IAuthService {

    String createToken(String openid);

    boolean checkToken(String token);

    String openid(String token);

    /**
     * 从 Token 提取 jti（JWT ID）；Token 无效或缺少 jti 时返回 null。
     */
    String extractJti(String token);

    /**
     * 从 Token 提取过期时间（epoch 毫秒）；无效或无过期声明时返回 0。
     */
    long extractExpiration(String token);
}

package com.dyx.market.domain.auth.service;

import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 鉴权服务：签发与校验 JWT，并在可用时检查吊销黑名单。
 */
@Slf4j
@Service
public class AuthService extends AbstractAuthService {

    private static final long TOKEN_TTL_MILLIS = 24 * 60 * 60 * 1000L;

    /** 兼容旧启动器；微服务场景由 TokenRevocationConfig 注入。 */
    @Autowired(required = false)
    private ITokenRevocationService tokenRevocationService;

    public AuthService(@Value("${app.jwt.secret:change-me-in-dev-only}") String jwtSecret) {
        super(jwtSecret);
    }

    @Override
    public String createToken(String openid) {
        java.util.Map<String, Object> claims = new java.util.HashMap<>();
        claims.put("openId", openid);
        return encode(openid, TOKEN_TTL_MILLIS, claims);
    }

    @Override
    public boolean checkToken(String token) {
        if (!isVerify(token)) {
            return false;
        }
        // 吊销黑名单检查（服务可用时）
        if (tokenRevocationService != null) {
            String jti = extractJti(token);
            if (jti != null && tokenRevocationService.isRevoked(jti)) {
                log.warn("[AuthService] token rejected - jti:{} is revoked", jti);
                return false;
            }
        }
        return true;
    }

    @Override
    public String openid(String token) {
        Claims claims = decode(token);
        return claims.get("openId").toString();
    }

    @Override
    public String extractJti(String token) {
        return extractJtiFromToken(token);
    }

    @Override
    public long extractExpiration(String token) {
        return extractExpirationFromToken(token);
    }
}

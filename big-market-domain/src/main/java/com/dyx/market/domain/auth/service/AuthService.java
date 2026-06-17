package com.dyx.market.domain.auth.service;

import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * @author Fuzhengwei bugstack.cn @小傅哥
 * @description 鉴权服务
 * @create 2024-10-07 17:55
 */
@Slf4j
@Service
public class AuthService extends AbstractAuthService {

    private static final long TOKEN_TTL_MILLIS = 24 * 60 * 60 * 1000L;

    /** Optional for legacy launchers; microservices get this from TokenRevocationConfig. */
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
        // check revocation blacklist if service is available
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

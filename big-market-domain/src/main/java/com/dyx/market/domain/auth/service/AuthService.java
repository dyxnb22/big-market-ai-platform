package com.dyx.market.domain.auth.service;

import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
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
        return isVerify(token);
    }

    @Override
    public String openid(String token) {
        Claims claims = decode(token);
        return claims.get("openId").toString();
    }

}

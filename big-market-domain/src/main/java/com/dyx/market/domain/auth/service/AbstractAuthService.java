package com.dyx.market.domain.auth.service;

import com.dyx.market.domain.auth.util.JwtTokenUtils;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * 鉴权抽象基类：基于 HS256 的 JWT 签发、解析与校验。
 * <p>
 * 子类实现 {@link IAuthService} 的业务入口；本类封装 encode/decode 及 jti、过期时间等载荷提取。
 */
@Slf4j
public abstract class AbstractAuthService implements IAuthService {

    private final SecretKey signingKey;

    protected AbstractAuthService(String jwtSecret) {
        this.signingKey = JwtTokenUtils.buildSigningKey(jwtSecret);
    }

    /**
     * 签发 JWT 字符串，包含 header、payload（iat/jti/iss/exp 等）与签名三部分。
     */
    protected String encode(String issuer, long ttlMillis, Map<String, Object> claims) {
        // iss 签发人，ttlMillis 生存时间，claims 为荷载中的扩展非隐私字段
        // 防御性拷贝：避免 jjwt 内部 put 操作污染调用方持有的 map
        Map<String, Object> payload = claims != null ? new HashMap<>(claims) : new HashMap<>();
        long nowMillis = System.currentTimeMillis();

        JwtBuilder builder = Jwts.builder()
                .setClaims(payload)
                .setId(UUID.randomUUID().toString())
                .setIssuedAt(new Date(nowMillis))
                .setSubject(issuer)
                .signWith(signingKey);
        if (ttlMillis > 0) {
            builder.setExpiration(new Date(nowMillis + ttlMillis));
        }
        return builder.compact();
    }

    // 解析 jwtToken，得到荷载部分所有键值对（Claim 即 map）
    protected Claims decode(String jwtToken) {
        return Jwts.parserBuilder()
                .setSigningKey(signingKey)
                .build()
                .parseClaimsJws(JwtTokenUtils.extractToken(jwtToken))
                .getBody();
    }

    // 判断 jwtToken 是否合法（使用 jjwt 统一实现，替代 auth0）
    protected boolean isVerify(String jwtToken) {
        try {
            decode(jwtToken);
            return true;
        } catch (Exception e) {
            log.error("jwt isVerify Err", e);
            return false;
        }
    }

    protected String extractJtiFromToken(String jwtToken) {
        return extractClaim(jwtToken, Claims::getId, null);
    }

    protected long extractExpirationFromToken(String jwtToken) {
        Date exp = extractClaim(jwtToken, Claims::getExpiration, null);
        return exp != null ? exp.getTime() : 0L;
    }

    private <T> T extractClaim(String jwtToken, Function<Claims, T> getter, T fallback) {
        try {
            return getter.apply(decode(jwtToken));
        } catch (Exception e) {
            log.error("Failed to extract claim from token", e);
            return fallback;
        }
    }

}

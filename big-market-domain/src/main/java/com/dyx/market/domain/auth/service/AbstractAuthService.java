package com.dyx.market.domain.auth.service;

import com.dyx.market.domain.auth.util.JwtTokenUtils;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 鉴权抽象基类：基于 HS256 的 JWT 签发、解析与校验。
 * <p>
 * 子类实现 {@link IAuthService} 的业务入口；本类封装 encode/decode 及 jti、过期时间等载荷提取。
 */
@Slf4j
public abstract class AbstractAuthService implements IAuthService {

    private final String base64EncodedSecretKey;

    protected AbstractAuthService(String jwtSecret) {
        this.base64EncodedSecretKey = Base64.encodeBase64String(jwtSecret.getBytes());
    }

    /**
     * 签发 JWT 字符串，包含 header、payload（iat/jti/iss/exp 等）与签名三部分。
     */
    protected String encode(String issuer, long ttlMillis, Map<String, Object> claims) {
        // iss 签发人，ttlMillis 生存时间，claims 为荷载中的扩展非隐私字段
        if (claims == null) {
            claims = new HashMap<>();
        }
        long nowMillis = System.currentTimeMillis();

        JwtBuilder builder = Jwts.builder()
                // 荷载部分
                .setClaims(claims)
                // JWT 唯一标识
                .setId(UUID.randomUUID().toString())//2.
                // 签发时间
                .setIssuedAt(new Date(nowMillis))
                // 签发人（逻辑上一般为 username 或 userId）
                .setSubject(issuer)
                .signWith(SignatureAlgorithm.HS256, base64EncodedSecretKey);//这个地方是生成jwt使用的算法和秘钥
        if (ttlMillis >= 0) {
            long expMillis = nowMillis + ttlMillis;
            Date exp = new Date(expMillis);// 4. 过期时间，这个也是使用毫秒生成的，使用当前时间+前面传入的持续时间生成
            builder.setExpiration(exp);
        }
        return builder.compact();
    }

    // 解析 jwtToken，得到荷载部分所有键值对（Claim 即 map）
    protected Claims decode(String jwtToken) {
        // 得到 DefaultJwtParser
        return Jwts.parser()
                // 设置签名的秘钥
                .setSigningKey(base64EncodedSecretKey)
                // 设置需要解析的 jwt
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
        try {
            Claims claims = decode(jwtToken);
            return claims.getId();
        } catch (Exception e) {
            log.error("Failed to extract jti from token", e);
            return null;
        }
    }

    protected long extractExpirationFromToken(String jwtToken) {
        try {
            Claims claims = decode(jwtToken);
            Date exp = claims.getExpiration();
            return exp != null ? exp.getTime() : 0L;
        } catch (Exception e) {
            log.error("Failed to extract expiration from token", e);
            return 0L;
        }
    }

}

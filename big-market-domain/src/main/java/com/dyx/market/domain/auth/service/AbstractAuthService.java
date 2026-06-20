package com.dyx.market.domain.auth.service;

import com.dyx.market.domain.auth.util.JwtTokenUtils;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
        this.signingKey = buildSigningKey(jwtSecret);
    }

    /**
     * jjwt 0.11+ requires HMAC keys >= 256 bits. Secrets shorter than 32 bytes are
     * deterministically stretched with SHA-256 (config value unchanged).
     *
     * <p><b>Migration note:</b> the old implementation (pre-refactor) used jjwt's legacy
     * {@code signWith(SignatureAlgorithm, base64String)} API which treated the secret as
     * a Base64-encoded key and decoded it — effectively using the raw secret bytes.
     * The new {@link SecretKeySpec} path also uses raw bytes for secrets ≥ 32 bytes, so
     * key material is identical and existing tokens remain valid.
     * For secrets &lt; 32 bytes the old API accepted them as-is while this implementation
     * SHA-256-stretches them, producing a different key — all previously-issued tokens
     * will fail verification after deployment. Ensure {@code JWT_SECRET} is ≥ 32 bytes
     * in all environments to avoid this.
     */
    private static SecretKey buildSigningKey(String jwtSecret) {
        byte[] raw = jwtSecret.getBytes(StandardCharsets.UTF_8);
        if (raw.length < 32) {
            log.warn("JWT secret is only {} bytes; existing tokens issued with the previous " +
                    "jjwt legacy API will be invalidated after this deployment. " +
                    "Set JWT_SECRET to a value of at least 32 characters to prevent this.", raw.length);
        }
        byte[] keyMaterial = raw.length >= 32 ? raw : sha256(raw);
        return new SecretKeySpec(keyMaterial, "HmacSHA256");
    }

    private static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
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

package com.dyx.market.domain.auth.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.apache.commons.lang3.StringUtils;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * JWT 请求头解析与 HS256 验签工具，与 {@link com.dyx.market.domain.auth.service.AbstractAuthService} 密钥派生一致。
 */
public final class JwtTokenUtils {

    private static final String BEARER_PREFIX = "Bearer ";

    private JwtTokenUtils() {
    }

    /**
     * 支持裸 JWT 或标准 {@code Authorization: Bearer <jwt>} 格式。
     */
    public static String extractToken(String authHeader) {
        if (StringUtils.isBlank(authHeader)) {
            return authHeader;
        }
        String trimmed = authHeader.trim();
        if (trimmed.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return trimmed.substring(BEARER_PREFIX.length()).trim();
        }
        return trimmed;
    }

    /**
     * 与 {@code AbstractAuthService} 相同的 HS256 密钥材料：≥32 字节用原文，否则 SHA-256 拉伸。
     */
    public static SecretKey buildSigningKey(String jwtSecret) {
        byte[] raw = jwtSecret.getBytes(StandardCharsets.UTF_8);
        byte[] keyMaterial = raw.length >= 32 ? raw : sha256(raw);
        return new SecretKeySpec(keyMaterial, "HmacSHA256");
    }

    public static Claims parseClaims(String authHeader, String jwtSecret) {
        String token = extractToken(authHeader);
        if (StringUtils.isBlank(token)) {
            return null;
        }
        return Jwts.parserBuilder()
                .setSigningKey(buildSigningKey(jwtSecret))
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    private static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}

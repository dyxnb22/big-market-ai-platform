package com.dyx.market.domain.auth.util;

import org.apache.commons.lang3.StringUtils;

/**
 * JWT 请求头解析工具，供鉴权控制器与 {@link com.dyx.market.domain.auth.service.AbstractAuthService} 共用。
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
}

package com.dyx.market.domain.auth.util;

import org.apache.commons.lang3.StringUtils;

/**
 * Shared JWT header helpers used by auth controllers and {@link com.dyx.market.domain.auth.service.AbstractAuthService}.
 */
public final class JwtTokenUtils {

    private static final String BEARER_PREFIX = "Bearer ";

    private JwtTokenUtils() {
    }

    /**
     * Accepts either a raw JWT or a standard {@code Authorization: Bearer <jwt>} value.
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

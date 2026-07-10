package com.dyx.market.chatbot.support;

import com.dyx.market.domain.auth.util.JwtTokenUtils;
import io.jsonwebtoken.Claims;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 从 Authorization 头解析 userId（openId），用于 chat 会话持久化。
 */
@Component
public class ChatTokenUserSupport {

    @Value("${app.jwt.secret:change-me-in-dev-only}")
    private String jwtSecret;

    public String resolveUserId(String authorizationHeader) {
        if (StringUtils.isBlank(authorizationHeader)) {
            return null;
        }
        try {
            Claims claims = JwtTokenUtils.parseClaims(authorizationHeader, jwtSecret);
            if (claims == null) {
                return null;
            }
            Object openId = claims.get("openId");
            return openId != null ? openId.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }
}

package com.dyx.market.trigger.support;

import com.dyx.market.domain.auth.service.IAuthService;
import com.dyx.market.domain.auth.util.JwtTokenUtils;
import com.dyx.market.types.enums.ResponseCode;
import com.dyx.market.types.exception.AppException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;

/**
 * 从 HTTP request 属性或 Authorization 头解析当前用户 ID。
 */
@Component
public class AuthenticatedUserSupport {

    private final IAuthService authService;
    private final HttpServletRequest httpServletRequest;

    public AuthenticatedUserSupport(IAuthService authService,
                                    @Autowired(required = false) HttpServletRequest httpServletRequest) {
        this.authService = authService;
        this.httpServletRequest = httpServletRequest;
    }

    public String requireUserId() {
        return requireUserId(null);
    }

    public String requireUserId(String authorizationHeader) {
        if (httpServletRequest != null) {
            String fromAttr = (String) httpServletRequest.getAttribute("userId");
            if (StringUtils.isNotBlank(fromAttr)) {
                return fromAttr;
            }
        }
        String token = resolveAuthorizationToken(authorizationHeader);
        token = JwtTokenUtils.extractToken(token);
        if (StringUtils.isNotBlank(token) && authService.checkToken(token)) {
            String openid = authService.openid(token);
            if (StringUtils.isNotBlank(openid)) {
                return openid;
            }
        }
        throw new AppException(ResponseCode.Login.TOKEN_ERROR.getCode(), ResponseCode.Login.TOKEN_ERROR.getInfo());
    }

    private String resolveAuthorizationToken(String authorizationHeader) {
        if (StringUtils.isNotBlank(authorizationHeader)) {
            return authorizationHeader;
        }
        if (httpServletRequest != null) {
            return httpServletRequest.getHeader("Authorization");
        }
        return null;
    }
}

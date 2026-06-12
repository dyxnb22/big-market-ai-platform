package com.dyx.market.config;

import com.dyx.market.domain.auth.service.IAuthService;
import com.dyx.market.types.enums.ResponseCode;
import com.dyx.market.trigger.api.response.Response;
import com.alibaba.fastjson.JSON;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Component
public class AdminAuthInterceptor implements HandlerInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";

    private final IAuthService authService;

    @Value("${app.admin.token:admin-dev-token}")
    private String adminToken;

    @Value("${app.admin.user-ids:}")
    private String adminUserIds;

    public AdminAuthInterceptor(IAuthService authService) {
        this.authService = authService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String authHeader = request.getHeader("Authorization");
        if (StringUtils.isBlank(authHeader)) {
            writeError(response, ResponseCode.Login.TOKEN_ERROR.getCode(), ResponseCode.Login.TOKEN_ERROR.getInfo());
            return false;
        }

        String token = authHeader.startsWith(BEARER_PREFIX)
                ? authHeader.substring(BEARER_PREFIX.length())
                : authHeader;

        if (!authService.checkToken(token)) {
            writeError(response, ResponseCode.Login.TOKEN_ERROR.getCode(), ResponseCode.Login.TOKEN_ERROR.getInfo());
            return false;
        }

        String openid = authService.openid(token);
        if (StringUtils.isBlank(openid)) {
            writeError(response, ResponseCode.Login.TOKEN_ERROR.getCode(), ResponseCode.Login.TOKEN_ERROR.getInfo());
            return false;
        }

        if (!isAdmin(openid, token)) {
            writeError(response, ResponseCode.APP_TOKEN_ERROR.getCode(), ResponseCode.APP_TOKEN_ERROR.getInfo());
            return false;
        }

        request.setAttribute("userId", openid);
        return true;
    }

    private boolean isAdmin(String openid, String token) {
        if (StringUtils.isNotBlank(adminToken) && adminToken.equals(token)) {
            return true;
        }
        if (StringUtils.isNotBlank(adminUserIds)) {
            List<String> ids = Arrays.asList(adminUserIds.split(","));
            return ids.contains(openid);
        }
        return false;
    }

    private void writeError(HttpServletResponse response, String code, String info) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(HttpServletResponse.SC_OK);
        response.getWriter().write(JSON.toJSONString(Response.builder()
                .code(code)
                .info(info)
                .build()));
    }

}

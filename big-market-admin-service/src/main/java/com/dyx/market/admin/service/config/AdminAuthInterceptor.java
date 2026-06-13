package com.dyx.market.admin.service.config;

import com.alibaba.fastjson.JSON;
import com.dyx.market.domain.auth.service.IAuthService;
import com.dyx.market.trigger.api.response.Response;
import com.dyx.market.types.enums.ResponseCode;
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

    @Value("${app.admin.user-ids:admin}")
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

        if (!isAdmin(openid)) {
            writeError(response, ResponseCode.APP_TOKEN_ERROR.getCode(), ResponseCode.APP_TOKEN_ERROR.getInfo());
            return false;
        }

        request.setAttribute("userId", openid);
        return true;
    }

    private boolean isAdmin(String openid) {
        if (StringUtils.isBlank(adminUserIds)) return false;
        return Arrays.asList(adminUserIds.split(",")).stream()
                .map(String::trim)
                .anyMatch(id -> id.equals(openid));
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

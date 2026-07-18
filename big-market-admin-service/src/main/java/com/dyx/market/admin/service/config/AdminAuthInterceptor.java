package com.dyx.market.admin.service.config;

import com.alibaba.fastjson.JSON;
import com.dyx.market.domain.auth.service.IAuthService;
import com.dyx.market.trigger.api.response.Response;
import com.dyx.market.types.enums.ResponseCode;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;

/**
 * 管理员鉴权拦截器：校验 Bearer Token 并确认用户属于管理员白名单。
 * <p>
 * 管理员 ID 列表绑定 {@code app.admin.user-ids}（默认 {@code admin}，逗号分隔）。
 * 校验通过后把 {@code userId} 写入请求属性。
 */
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

        // 兼容带或不带 "Bearer " 前缀的 Token
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
        response.setStatus(com.dyx.market.types.web.ResponseHttpStatusMapper.toStatusCode(code));
        response.getWriter().write(JSON.toJSONString(Response.builder()
                .code(code)
                .info(info)
                .build()));
    }

}

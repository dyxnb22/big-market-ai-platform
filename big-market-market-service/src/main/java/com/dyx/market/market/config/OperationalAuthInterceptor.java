package com.dyx.market.market.config;

import com.alibaba.fastjson.JSON;
import com.dyx.market.domain.auth.service.AdminAccessService;
import com.dyx.market.types.common.OperationalAuthConstants;
import com.dyx.market.trigger.api.response.Response;
import com.dyx.market.types.enums.ResponseCode;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 运营类接口鉴权：ERP、DCC、armory 等路径统一校验管理员身份。
 */
@Component
public class OperationalAuthInterceptor implements HandlerInterceptor {

    private final AdminAccessService adminAccessService;

    public OperationalAuthInterceptor(AdminAccessService adminAccessService) {
        this.adminAccessService = adminAccessService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!adminAccessService.hasAdminAccess(
                request.getHeader("X-Admin-Token"), request.getHeader("Authorization"))) {
            writeError(response);
            return false;
        }
        request.setAttribute(OperationalAuthConstants.AUTH_PASSED_ATTR, Boolean.TRUE);
        return true;
    }

    private void writeError(HttpServletResponse response) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.getWriter().write(JSON.toJSONString(Response.builder()
                .code(ResponseCode.APP_TOKEN_ERROR.getCode())
                .info(ResponseCode.APP_TOKEN_ERROR.getInfo())
                .build()));
    }
}

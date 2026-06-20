package com.dyx.market.domain.auth.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;

/**
 * 运营/管理端鉴权：支持静态 {@code X-Admin-Token} 或管理员 JWT。
 */
@Slf4j
@Service
public class AdminAccessService {

    @Value("${erp.admin.token:${app.admin.token:admin-dev-token}}")
    private String adminToken;

    @Value("${app.admin.user-ids:admin}")
    private String adminUserIds;

    private final IAuthService authService;

    @Autowired
    public AdminAccessService(IAuthService authService) {
        this.authService = authService;
    }

    /** 单元测试用：注入 token 与白名单，避免依赖 Spring 属性绑定。 */
    AdminAccessService(IAuthService authService, String adminToken, String adminUserIds) {
        this.authService = authService;
        this.adminToken = adminToken;
        this.adminUserIds = adminUserIds;
    }

    public boolean hasAdminAccess(String xAdminToken, String authorizationHeader) {
        if (StringUtils.isNotBlank(xAdminToken) && adminToken.equals(xAdminToken)) {
            return true;
        }
        if (StringUtils.isBlank(authorizationHeader)) {
            return false;
        }
        String jwtToken = authorizationHeader.startsWith("Bearer ")
                ? authorizationHeader.substring(7)
                : authorizationHeader;
        try {
            if (!authService.checkToken(jwtToken)) {
                return false;
            }
            String openid = authService.openid(jwtToken);
            return isAdminUser(openid);
        } catch (Exception e) {
            log.warn("AdminAccessService JWT check failed: {}", e.getMessage());
            return false;
        }
    }

    public boolean isAdminUser(String openid) {
        if (StringUtils.isBlank(openid) || StringUtils.isBlank(adminUserIds)) {
            return false;
        }
        return Arrays.stream(adminUserIds.split(","))
                .map(String::trim)
                .filter(StringUtils::isNotBlank)
                .anyMatch(id -> id.equals(openid));
    }
}

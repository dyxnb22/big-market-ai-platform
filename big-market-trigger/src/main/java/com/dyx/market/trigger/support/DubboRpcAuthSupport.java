package com.dyx.market.trigger.support;

import com.dyx.market.domain.auth.service.AdminAccessService;
import com.dyx.market.types.enums.ResponseCode;
import com.dyx.market.types.exception.AppException;

/**
 * Dubbo RPC 入口的管理员鉴权（HTTP 由 {@code OperationalAuthInterceptor} 负责）。
 */
public final class DubboRpcAuthSupport {

    private DubboRpcAuthSupport() {
    }

    public static void requireAdmin(AdminAccessService adminAccessService, String xAdminToken) {
        if (adminAccessService == null) {
            throw new AppException(ResponseCode.APP_TOKEN_ERROR.getCode(), ResponseCode.APP_TOKEN_ERROR.getInfo());
        }
        if (!adminAccessService.hasAdminAccess(xAdminToken, null)) {
            throw new AppException(ResponseCode.APP_TOKEN_ERROR.getCode(), ResponseCode.APP_TOKEN_ERROR.getInfo());
        }
    }

    public static void rejectInternalRpc(String method) {
        throw new AppException(ResponseCode.APP_TOKEN_ERROR.getCode(),
                "RPC method requires authenticated HTTP gateway: " + method);
    }
}

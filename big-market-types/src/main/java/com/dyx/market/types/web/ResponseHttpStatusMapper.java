package com.dyx.market.types.web;

import org.springframework.http.HttpStatus;

/**
 * 将平台业务 {@code code} 映射为 HTTP 状态，同时保留 JSON 响应体中的业务码。
 *
 * <p>HTTP 状态只表达传输层结果，具体业务码仍由响应体提供；未识别或空业务码统一按
 * 服务器内部错误处理。</p>
 */
public final class ResponseHttpStatusMapper {

    private ResponseHttpStatusMapper() {
    }

    /** 根据统一响应业务码返回对应的 HTTP 状态。 */
    public static HttpStatus toHttpStatus(String code) {
        if (code == null || code.isEmpty()) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
        if ("0000".equals(code)) {
            return HttpStatus.OK;
        }
        if ("0002".equals(code)) {
            return HttpStatus.BAD_REQUEST;
        }
        if ("0003".equals(code)) {
            return HttpStatus.CONFLICT;
        }
        if ("0005".equals(code)) {
            return HttpStatus.TOO_MANY_REQUESTS;
        }
        if ("0006".equals(code) || "0007".equals(code)) {
            return HttpStatus.SERVICE_UNAVAILABLE;
        }
        if ("0008".equals(code)) {
            return HttpStatus.FORBIDDEN;
        }
        if ("0009".equals(code)) {
            return HttpStatus.UNAUTHORIZED;
        }
        if ("0001".equals(code) || "0004".equals(code)) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
        if (code.startsWith("ERR_")) {
            return HttpStatus.UNPROCESSABLE_ENTITY;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    /** 根据统一响应业务码返回对应的 HTTP 数字状态码。 */
    public static int toStatusCode(String code) {
        return toHttpStatus(code).value();
    }
}

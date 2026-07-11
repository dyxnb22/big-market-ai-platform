package com.dyx.market.types.web;

import org.springframework.http.HttpStatus;

/**
 * Maps platform business {@code code} values to HTTP status while keeping JSON body codes.
 */
public final class ResponseHttpStatusMapper {

    private ResponseHttpStatusMapper() {
    }

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

    public static int toStatusCode(String code) {
        return toHttpStatus(code).value();
    }
}

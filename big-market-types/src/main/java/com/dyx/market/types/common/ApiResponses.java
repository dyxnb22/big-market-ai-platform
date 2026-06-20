package com.dyx.market.types.common;

import com.dyx.market.types.enums.ResponseCode;

/**
 * 统一 API 成功/失败响应构建，减少 Controller 样板代码。
 */
public final class ApiResponses {

    private ApiResponses() {
    }

    public static <T> Response<T> ok(T data) {
        return Response.<T>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(data)
                .build();
    }

    public static <T> Response<T> ok() {
        return ok(null);
    }

    public static <T> Response<T> of(String code, String info, T data) {
        return Response.<T>builder()
                .code(code)
                .info(info)
                .data(data)
                .build();
    }

    public static <T> Response<T> fail(ResponseCode code) {
        return of(code.getCode(), code.getInfo(), null);
    }

    public static <T> Response<T> fail(String code, String info) {
        return of(code, info, null);
    }
}

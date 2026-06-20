package com.dyx.market.trigger.http;

import com.dyx.market.trigger.api.response.Response;
import com.dyx.market.trigger.api.support.ApiResponses;
import com.dyx.market.types.enums.ResponseCode;

/**
 * 统一 API 成功/失败响应构建（trigger.api.response.Response）。
 */
public final class TriggerApiResponses {

    private TriggerApiResponses() {
    }

    public static <T> Response<T> ok(T data) {
        return ApiResponses.ok(data);
    }

    public static <T> Response<T> ok() {
        return ApiResponses.ok();
    }

    public static <T> Response<T> of(String code, String info, T data) {
        return ApiResponses.of(code, info, data);
    }

    public static <T> Response<T> fail(ResponseCode code) {
        return ApiResponses.fail(code, null);
    }
}

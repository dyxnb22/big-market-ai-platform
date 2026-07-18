package com.dyx.market.trigger.api.support;

import com.dyx.market.trigger.api.response.Response;
import com.dyx.market.types.enums.ResponseCode;
import com.dyx.market.types.exception.AppException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

/**
 * 统一 Dubbo/HTTP 响应构建与异常包装。
 */
public final class ApiResponses {

    private static final Logger log = LoggerFactory.getLogger(ApiResponses.class);

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
        return Response.<T>builder().code(code).info(info).data(data).build();
    }

    public static <T> Response<T> fail(AppException e) {
        return Response.<T>builder().code(e.getCode()).info(e.getInfo()).build();
    }

    public static <T> Response<T> fail(ResponseCode code, T data) {
        return of(code.getCode(), code.getInfo(), data);
    }

    public static <T> Response<T> execute(Supplier<T> supplier) {
        try {
            return ok(supplier.get());
        } catch (AppException e) {
            return fail(e);
        } catch (Exception e) {
            log.error("Unexpected exception while executing RPC/API handler", e);
            return of(ResponseCode.UN_ERROR.getCode(), ResponseCode.UN_ERROR.getInfo(), null);
        }
    }

    public static Response<Void> executeVoid(Runnable runnable) {
        try {
            runnable.run();
            return ok();
        } catch (AppException e) {
            return fail(e);
        } catch (Exception e) {
            log.error("Unexpected exception while executing RPC/API handler", e);
            return of(ResponseCode.UN_ERROR.getCode(), ResponseCode.UN_ERROR.getInfo(), null);
        }
    }
}

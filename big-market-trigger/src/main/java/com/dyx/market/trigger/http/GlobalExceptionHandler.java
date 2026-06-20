package com.dyx.market.trigger.http;

import com.dyx.market.types.enums.ResponseCode;
import com.dyx.market.types.exception.AppException;
import com.dyx.market.trigger.api.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 统一 HTTP 异常处理，减少 Controller 内重复的 try/catch 样板代码。
 */
@Slf4j
@RestControllerAdvice(basePackages = "com.dyx.market.trigger.http")
public class GlobalExceptionHandler {

    @ExceptionHandler(AppException.class)
    public Response<Void> handleAppException(AppException e) {
        log.warn("业务异常 code:{} info:{}", e.getCode(), e.getInfo());
        return Response.<Void>builder()
                .code(e.getCode())
                .info(e.getInfo())
                .build();
    }

    @ExceptionHandler(Exception.class)
    public Response<Void> handleException(Exception e) {
        log.error("系统异常", e);
        return Response.<Void>builder()
                .code(ResponseCode.UN_ERROR.getCode())
                .info(ResponseCode.UN_ERROR.getInfo())
                .build();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public Response<Void> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("参数异常: {}", e.getMessage());
        return Response.<Void>builder()
                .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                .info(ResponseCode.ILLEGAL_PARAMETER.getInfo())
                .build();
    }
}

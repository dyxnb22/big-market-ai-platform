package com.dyx.market.admin.config;

import com.dyx.market.types.enums.ResponseCode;
import com.dyx.market.types.exception.AppException;
import com.dyx.market.trigger.api.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.io.IOException;

/**
 * 管理端模块统一 HTTP 异常处理。
 *
 * <p>将 {@link AppException} 与系统异常映射为统一 {@link Response}，减少 Controller 内重复 try/catch。</p>
 */
@Slf4j
@RestControllerAdvice(basePackages = "com.dyx.market.admin")
public class AdminExceptionHandler {

    @ExceptionHandler(AppException.class)
    public Response<Void> handleAppException(AppException e) {
        log.warn("Admin business error code:{} info:{}", e.getCode(), e.getInfo());
        return Response.<Void>builder().code(e.getCode()).info(e.getInfo()).build();
    }

    @ExceptionHandler(IOException.class)
    public Response<Void> handleIOException(IOException e) {
        log.error("Admin config I/O error", e);
        return Response.<Void>builder()
                .code(ResponseCode.UN_ERROR.getCode())
                .info(ResponseCode.UN_ERROR.getInfo())
                .build();
    }

    @ExceptionHandler(Exception.class)
    public Response<Void> handleException(Exception e) {
        log.error("Admin system error", e);
        return Response.<Void>builder()
                .code(ResponseCode.UN_ERROR.getCode())
                .info(ResponseCode.UN_ERROR.getInfo())
                .build();
    }
}

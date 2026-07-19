package com.dyx.market.admin.config;

import com.dyx.market.types.enums.ResponseCode;
import com.dyx.market.types.exception.AppException;
import com.dyx.market.trigger.api.response.Response;
import com.dyx.market.types.web.ResponseHttpStatusMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<Response<Void>> handleAppException(AppException e) {
        log.warn("Admin business error code:{} info:{}", e.getCode(), e.getInfo());
        Response<Void> body = Response.<Void>builder().code(e.getCode()).info(e.getInfo()).build();
        return ResponseEntity.status(ResponseHttpStatusMapper.toHttpStatus(e.getCode())).body(body);
    }

    @ExceptionHandler(IOException.class)
    public ResponseEntity<Response<Void>> handleIOException(IOException e) {
        log.error("Admin config I/O error", e);
        Response<Void> body = Response.<Void>builder()
                .code(ResponseCode.UN_ERROR.getCode())
                .info(ResponseCode.UN_ERROR.getInfo())
                .build();
        return ResponseEntity.status(ResponseHttpStatusMapper.toHttpStatus(body.getCode())).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Response<Void>> handleException(Exception e) {
        log.error("Admin system error", e);
        Response<Void> body = Response.<Void>builder()
                .code(ResponseCode.UN_ERROR.getCode())
                .info(ResponseCode.UN_ERROR.getInfo())
                .build();
        return ResponseEntity.status(ResponseHttpStatusMapper.toHttpStatus(body.getCode())).body(body);
    }
}

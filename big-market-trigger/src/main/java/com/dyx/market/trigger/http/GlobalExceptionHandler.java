package com.dyx.market.trigger.http;

import com.dyx.market.types.enums.ResponseCode;
import com.dyx.market.types.exception.AppException;
import com.dyx.market.trigger.api.response.Response;
import com.dyx.market.types.web.ResponseHttpStatusMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 统一 HTTP 异常处理，减少 Controller 内重复的 try/catch 样板代码。
 */
@Slf4j
@RestControllerAdvice(basePackages = "com.dyx.market.trigger.http")
public class GlobalExceptionHandler {

    @ExceptionHandler(AppException.class)
    public ResponseEntity<Response<Void>> handleAppException(AppException e) {
        log.warn("业务异常 code:{} info:{}", e.getCode(), e.getInfo());
        Response<Void> body = Response.<Void>builder()
                .code(e.getCode())
                .info(e.getInfo())
                .build();
        return ResponseEntity.status(ResponseHttpStatusMapper.toHttpStatus(e.getCode())).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Response<Void>> handleException(Exception e) {
        log.error("系统异常", e);
        Response<Void> body = Response.<Void>builder()
                .code(ResponseCode.UN_ERROR.getCode())
                .info(ResponseCode.UN_ERROR.getInfo())
                .build();
        return ResponseEntity.status(ResponseHttpStatusMapper.toHttpStatus(body.getCode())).body(body);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Response<Void>> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("参数异常: {}", e.getMessage());
        Response<Void> body = Response.<Void>builder()
                .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                .info(ResponseCode.ILLEGAL_PARAMETER.getInfo())
                .build();
        return ResponseEntity.status(ResponseHttpStatusMapper.toHttpStatus(body.getCode())).body(body);
    }
}

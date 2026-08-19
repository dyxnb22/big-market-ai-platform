package com.dyx.market.starter.web.config;

import com.dyx.market.types.web.ResponseHttpStatusMapper;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.lang.reflect.Method;

/**
 * 根据统一 JSON {@code code} 为 {@code Response} 响应体设置 HTTP 状态。
 * 直接写 JSON 的拦截器应自行调用 {@link ResponseHttpStatusMapper} 完成映射。
 */
@ControllerAdvice
public class ResponseHttpStatusAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        String code = extractCode(body);
        if (code != null) {
            response.setStatusCode(ResponseHttpStatusMapper.toHttpStatus(code));
        }
        return body;
    }

    private static String extractCode(Object body) {
        if (body == null) {
            return null;
        }
        try {
            Method getter = body.getClass().getMethod("getCode");
            Object value = getter.invoke(body);
            return value == null ? null : String.valueOf(value);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }
}

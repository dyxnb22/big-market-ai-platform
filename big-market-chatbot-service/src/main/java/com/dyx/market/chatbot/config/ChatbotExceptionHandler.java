package com.dyx.market.chatbot.config;

import com.dyx.market.chatbot.support.ChatbotErrorResponseSupport;
import com.dyx.market.trigger.api.dto.ChatbotAskResponseDTO;
import com.dyx.market.types.enums.ResponseCode;
import com.dyx.market.types.exception.AppException;
import com.dyx.market.trigger.api.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Optional;

/**
 * Chatbot 模块统一 HTTP 异常处理。
 *
 * <p>对 /ask 端点的业务异常返回带积分余额的 {@link ChatbotAskResponseDTO} 错误体，
 * 其余接口返回无 data 的标准 {@link Response}。</p>
 */
@Slf4j
@RestControllerAdvice(basePackages = "com.dyx.market.chatbot")
public class ChatbotExceptionHandler {

    /**
     * 由 {@link ChatbotController#ask} 设置的请求属性，标识当前为 /ask 请求。
     * 避免依赖 URI 后缀匹配，在反向代理改写路径时仍能返回正确错误体。
     */
    public static final String ATTR_ASK_ENDPOINT = "chatbot.isAskEndpoint";

    @Resource
    private ChatbotErrorResponseSupport chatbotErrorResponseSupport;

    @ExceptionHandler(AppException.class)
    @SuppressWarnings("java:S1452")
    public Response<?> handleAppException(AppException e, WebRequest request) {
        log.warn("Chatbot business error code:{} info:{}", e.getCode(), e.getInfo());
        if (isAskEndpoint(request)) {
            String token = extractAuthorizationToken(request);
            ChatbotAskResponseDTO errorData = chatbotErrorResponseSupport.buildErrorData(e, token);
            return Response.<ChatbotAskResponseDTO>builder()
                    .code(e.getCode())
                    .info(e.getInfo())
                    .data(errorData)
                    .build();
        }
        return Response.<Void>builder().code(e.getCode()).info(e.getInfo()).build();
    }

    @ExceptionHandler(Exception.class)
    public Response<Void> handleException(Exception e) {
        log.error("Chatbot system error", e);
        return Response.<Void>builder()
                .code(ResponseCode.UN_ERROR.getCode())
                .info(ResponseCode.UN_ERROR.getInfo())
                .build();
    }

    private boolean isAskEndpoint(WebRequest request) {
        return toServlet(request)
                .map(r -> Boolean.TRUE.equals(r.getAttribute(ATTR_ASK_ENDPOINT)))
                .orElse(false);
    }

    private String extractAuthorizationToken(WebRequest request) {
        return request instanceof ServletWebRequest
                ? ((ServletWebRequest) request).getHeader("Authorization")
                : null;
    }

    private Optional<HttpServletRequest> toServlet(WebRequest request) {
        return request instanceof ServletWebRequest
                ? Optional.of(((ServletWebRequest) request).getRequest())
                : Optional.empty();
    }
}

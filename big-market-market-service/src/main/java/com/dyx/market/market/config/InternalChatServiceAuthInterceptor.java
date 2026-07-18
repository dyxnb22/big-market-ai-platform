package com.dyx.market.market.config;

import com.dyx.market.types.enums.ResponseCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 校验 chatbot / message-job 调用内部积分接口的服务令牌。
 */
@Slf4j
@Component
public class InternalChatServiceAuthInterceptor implements HandlerInterceptor {

    @Value("${chat.internal-service-token:change-me-chat-internal}")
    private String internalServiceToken;

    @Resource
    private ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String token = request.getHeader("X-Chat-Internal-Token");
        if (StringUtils.isBlank(internalServiceToken)
                || !StringUtils.equals(internalServiceToken, token)) {
            log.warn("内部 Chat 积分接口鉴权失败 path:{}", request.getRequestURI());
            writeUnauthorized(response);
            return false;
        }
        return true;
    }

    private void writeUnauthorized(HttpServletResponse response) throws Exception {
        String code = ResponseCode.Login.TOKEN_ERROR.getCode();
        response.setStatus(com.dyx.market.types.web.ResponseHttpStatusMapper.toStatusCode(code));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        Map<String, Object> body = new HashMap<>();
        body.put("code", code);
        body.put("info", "内部服务令牌无效");
        body.put("data", null);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}

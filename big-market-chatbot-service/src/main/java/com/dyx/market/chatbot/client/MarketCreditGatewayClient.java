package com.dyx.market.chatbot.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.Resource;
import java.math.BigDecimal;
import java.net.URLEncoder;

/**
 * 经网关调用 market-service 积分 API。
 */
@Slf4j
@Component
public class MarketCreditGatewayClient {

    @Value("${chatbot.gateway-url:http://127.0.0.1:8080}")
    private String gatewayUrl;

    @Value("${app.config.api-version:v1}")
    private String apiVersion;

    @Value("${chat.internal-service-token:change-me-chat-internal}")
    private String internalServiceToken;

    @Resource
    private RestTemplate restTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public BigDecimal fetchCreditBalance(String token) {
        if (StringUtils.isBlank(token)) {
            return BigDecimal.ZERO;
        }
        String url = baseUrl() + "/raffle/activity/query_user_credit_account_by_token";
        HttpHeaders headers = authHeaders(token);
        ResponseEntity<String> resp = restTemplate.postForEntity(url, new HttpEntity<>("{}", headers), String.class);
        return parseBalance(resp);
    }

    public BigDecimal deductCredit(String token, int amount, String requestId) {
        String url = baseUrl() + "/raffle/activity/chat_credit_deduct_by_token?amount=" + amount
                + "&requestId=" + urlEncode(requestId);
        ResponseEntity<String> resp = restTemplate.postForEntity(url, new HttpEntity<>("{}", authHeaders(token)), String.class);
        return parseBalance(resp);
    }

    public void refundCredit(String token, String originalRequestId) {
        String url = internalBaseUrl() + "/chat_credit_refund_by_token?originalRequestId="
                + urlEncode(originalRequestId);
        try {
            ResponseEntity<String> resp = restTemplate.postForEntity(
                    url, new HttpEntity<>("{}", internalAuthHeaders(token)), String.class);
            parseBalance(resp);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Failed to refund credit for requestId:{}", originalRequestId, e);
            throw new IllegalStateException("积分退还请求失败", e);
        }
    }

    public void markRefundPending(String token, String requestId) {
        String url = internalBaseUrl() + "/chat_credit_mark_refund_pending_by_token?requestId="
                + urlEncode(requestId);
        try {
            ResponseEntity<String> resp = restTemplate.postForEntity(
                    url, new HttpEntity<>("{}", internalAuthHeaders(token)), String.class);
            if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
                throw new IllegalStateException("标记退款 pending 失败");
            }
            JsonNode root = objectMapper.readTree(resp.getBody());
            if (!"0000".equals(root.path("code").asText())) {
                throw new IllegalStateException(root.path("info").asText("标记退款 pending 失败"));
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Failed to mark refund pending requestId:{}", requestId, e);
            throw new IllegalStateException("标记退款 pending 失败", e);
        }
    }

    private String baseUrl() {
        return gatewayUrl.replaceAll("/$", "") + "/api/" + apiVersion;
    }

    private String internalBaseUrl() {
        return baseUrl() + "/internal/raffle/activity";
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", token);
        return headers;
    }

    private HttpHeaders internalAuthHeaders(String token) {
        HttpHeaders headers = authHeaders(token);
        headers.set("X-Chat-Internal-Token", internalServiceToken);
        return headers;
    }

    private BigDecimal parseBalance(ResponseEntity<String> resp) {
        try {
            if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
                JsonNode root = objectMapper.readTree(resp.getBody());
                if ("0000".equals(root.path("code").asText()) && !root.path("data").isNull()) {
                    return new BigDecimal(root.path("data").asText());
                }
                throw new IllegalStateException(root.path("info").asText("积分操作失败"));
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Failed to parse credit response", e);
        }
        throw new IllegalStateException("积分操作失败");
    }

    private static String urlEncode(String val) {
        try {
            return URLEncoder.encode(val, "UTF-8");
        } catch (Exception e) {
            return val;
        }
    }
}

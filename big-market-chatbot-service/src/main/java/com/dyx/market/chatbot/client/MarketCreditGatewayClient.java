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

import javax.annotation.Resource;
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

    public void refundCredit(String token, int amount, String originalRequestId) {
        String url = baseUrl() + "/raffle/activity/chat_credit_refund_by_token?amount=" + amount
                + "&originalRequestId=" + urlEncode(originalRequestId);
        try {
            ResponseEntity<String> resp = restTemplate.postForEntity(url, new HttpEntity<>("{}", authHeaders(token)), String.class);
            parseBalance(resp);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Failed to refund credit for requestId:{} amount:{}", originalRequestId, amount, e);
            throw new IllegalStateException("积分退还请求失败", e);
        }
    }

    private String baseUrl() {
        return gatewayUrl.replaceAll("/$", "") + "/api/" + apiVersion;
    }

    private static HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", token);
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

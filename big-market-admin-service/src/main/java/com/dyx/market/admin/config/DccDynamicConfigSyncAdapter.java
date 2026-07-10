package com.dyx.market.admin.config;

import com.dyx.market.management.config.DynamicConfigSyncPort;
import com.dyx.market.types.enums.ResponseCode;
import com.dyx.market.types.exception.AppException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;

/**
 * Admin → market-service DCC HTTP sync for {@code degradeSwitch}.
 */
@Component
public class DccDynamicConfigSyncAdapter implements DynamicConfigSyncPort {

    private static final Logger log = LoggerFactory.getLogger(DccDynamicConfigSyncAdapter.class);

    @Value("${gateway.config.apiHost:http://127.0.0.1:8080}")
    private String gatewayApiHost;

    @Value("${app.config.api-version:v1}")
    private String apiVersion;

    @Value("${app.admin.token:admin-dev-token}")
    private String adminToken;

    @Resource
    private RestTemplate restTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void syncDegradeSwitch(String value) {
        syncDccKey("degradeSwitch", value);
    }

    @Override
    public void syncRateLimiterSwitch(String value) {
        syncDccKey("rateLimiterSwitch", value);
    }

    private void syncDccKey(String key, String value) {
        if (StringUtils.isBlank(value)) {
            return;
        }
        String url = UriComponentsBuilder.fromHttpUrl(normalizeGatewayHost())
                .path("/api/")
                .path(apiVersion)
                .path("/raffle/dcc/update_config")
                .queryParam("key", key)
                .queryParam("value", value)
                .encode(StandardCharsets.UTF_8)
                .build()
                .toUriString();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Admin-Token", adminToken);
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, new HttpEntity<>("{}", headers), String.class);
            if (!response.getStatusCode().is2xxSuccessful() || !isSuccessBody(response.getBody())) {
                throw new AppException(ResponseCode.UN_ERROR.getCode(),
                        "DCC sync failed for " + key + ": status=" + response.getStatusCodeValue()
                                + " body=" + response.getBody());
            }
            log.info("DCC {} synced to {}", key, value);
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            throw new AppException(ResponseCode.UN_ERROR.getCode(),
                    "DCC sync failed for " + key + ": " + e.getMessage());
        }
    }

    private boolean isSuccessBody(String body) {
        if (StringUtils.isBlank(body)) {
            return false;
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            return "0000".equals(root.path("code").asText());
        } catch (Exception e) {
            return false;
        }
    }

    private String normalizeGatewayHost() {
        String host = StringUtils.defaultIfBlank(gatewayApiHost, "http://127.0.0.1:8080");
        return host.endsWith("/") ? host.substring(0, host.length() - 1) : host;
    }
}

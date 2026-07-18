package com.dyx.market.infrastructure.adapter.repository;

import com.alibaba.fastjson.JSON;
import com.dyx.market.infrastructure.redis.IRedisService;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.util.concurrent.TimeUnit;

/**
 * Chatbot requestId 幂等：processing / completed 缓存，按用户隔离，避免重放重复调 AI。
 */
@Component
public class ChatRequestIdempotencySupport {

    private static final String KEY_PREFIX = "chat:request:";
    private static final long TTL_MILLIS = TimeUnit.DAYS.toMillis(7);

    @Resource
    private IRedisService redisService;

    public CachedChatResponse findCompleted(String userId, String requestId) {
        if (StringUtils.isBlank(userId) || StringUtils.isBlank(requestId)) {
            return null;
        }
        String raw = redisService.getValue(key(userId, requestId));
        if (StringUtils.isBlank(raw)) {
            return null;
        }
        CachedChatResponse cached = JSON.parseObject(raw, CachedChatResponse.class);
        if (cached != null && "completed".equals(cached.getStatus())) {
            return cached;
        }
        return null;
    }

    /**
     * 原子占位 processing；已存在（processing 或 completed）则返回 false。
     */
    public boolean tryMarkProcessing(String userId, String requestId) {
        if (StringUtils.isBlank(userId) || StringUtils.isBlank(requestId)) {
            return false;
        }
        String processingJson = JSON.toJSONString(CachedChatResponse.builder().status("processing").build());
        return Boolean.TRUE.equals(redisService.setValueIfAbsent(
                key(userId, requestId), processingJson, TTL_MILLIS, TimeUnit.MILLISECONDS));
    }

    public void complete(String userId, String requestId, CachedChatResponse response) {
        if (StringUtils.isBlank(userId) || StringUtils.isBlank(requestId) || response == null) {
            return;
        }
        response.setStatus("completed");
        redisService.setValue(key(userId, requestId), JSON.toJSONString(response), TTL_MILLIS);
    }

    /**
     * 扣费/校验失败等可重试路径：仅清除 processing 占位，不缓存 completed。
     */
    public void clearProcessing(String userId, String requestId) {
        if (StringUtils.isBlank(userId) || StringUtils.isBlank(requestId)) {
            return;
        }
        String redisKey = key(userId, requestId);
        String raw = redisService.getValue(redisKey);
        if (StringUtils.isBlank(raw)) {
            return;
        }
        CachedChatResponse cached = JSON.parseObject(raw, CachedChatResponse.class);
        if (cached != null && "processing".equals(cached.getStatus())) {
            redisService.remove(redisKey);
        }
    }

    private static String key(String userId, String requestId) {
        return KEY_PREFIX + userId + ":" + requestId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CachedChatResponse {
        private String status;
        private String answer;
        private String toolName;
        private boolean success;
        private java.math.BigDecimal creditDeducted;
        private java.math.BigDecimal creditBalance;
        private String errorCode;
        private String errorMessage;
    }
}

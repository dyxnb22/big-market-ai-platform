package com.dyx.market.chatbot.application;

import com.dyx.market.chatbot.client.MarketCreditGatewayClient;
import com.dyx.market.chatbot.support.ChatTokenUserSupport;
import com.dyx.market.infrastructure.adapter.repository.ChatCreditSessionSupport;
import com.dyx.market.infrastructure.adapter.repository.ChatRequestIdempotencySupport;
import com.dyx.market.management.config.PlatformConfigService;
import com.dyx.market.trigger.api.dto.ChatbotAskRequestDTO;
import com.dyx.market.trigger.api.dto.ChatbotAskResponseDTO;
import com.dyx.market.types.enums.ResponseCode;
import com.dyx.market.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.Resource;
import java.math.BigDecimal;
import java.util.*;

/**
 * AI Chatbot 应用服务：积分扣减、调用 AI 提供商、失败退还。
 *
 * <p>支持 DeepSeek 远程调用与本地兜底回复；开关与计费配置由 {@link PlatformConfigService} 动态下发。</p>
 */
@Slf4j
@Service
public class ChatbotApplicationService {

    private static final String CONFIG_NS_CHATBOT = "chatbot";
    private static final String PROVIDER_DEEPSEEK = "deepseek";
    private static final String JSON_KEY_CONTENT = "content";
    private static final String DEFAULT_PROVIDER = "local";
    private static final String DEFAULT_API_KEY = "";
    private static final String DEFAULT_BASE_URL = "https://api.deepseek.com";
    private static final String DEFAULT_MODEL = "deepseek-chat";
    /** Local canned replies are a free learning fallback; only a configured remote provider may charge. */
    private static final int DEFAULT_COST_PER_ASK = 0;

    @Resource
    private PlatformConfigService platformConfigService;
    @Resource
    private MarketCreditGatewayClient marketCreditGatewayClient;
    @Resource
    private ChatCreditSessionSupport chatCreditSessionSupport;
    @Resource
    private ChatRequestIdempotencySupport chatRequestIdempotencySupport;
    @Resource
    private ChatTokenUserSupport chatTokenUserSupport;
    @Resource
    private RestTemplate restTemplate;

    /**
     * AI 对话主流程：校验开关与参数 → 扣减积分 → 调用 AI → 返回回答。
     * <p>AI 调用失败时按 requestId 退还已扣积分；退款 HTTP 失败则标记 pending 由补偿 Job 处理。</p>
     */
    public ChatbotAskResponseDTO ask(ChatbotAskRequestDTO request, String token) {
        if (null == request || StringUtils.isBlank(request.getMessage())) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), ResponseCode.ILLEGAL_PARAMETER.getInfo());
        }
        ChatbotRuntimeConfig runtimeConfig = ChatbotRuntimeConfig.from(
                platformConfigService.snapshotValues(CONFIG_NS_CHATBOT));
        if (!runtimeConfig.enabled) {
            return disabledResponse(token);
        }

        int configuredCost = parseCostConfig(runtimeConfig.costPerAsk);
        int effectiveCost = isChargeableRemoteProvider(runtimeConfig) ? configuredCost : 0;
        if (effectiveCost > 0 && StringUtils.isBlank(token)) {
            throw new AppException(ResponseCode.Login.TOKEN_ERROR.getCode(), ResponseCode.Login.TOKEN_ERROR.getInfo());
        }
        String userId = resolveUserIdForAsk(token, effectiveCost);
        String requestId = StringUtils.defaultIfBlank(request.getRequestId(), UUID.randomUUID().toString());

        ChatRequestIdempotencySupport.CachedChatResponse cached =
                chatRequestIdempotencySupport.findCompleted(userId, requestId);
        if (cached != null) {
            return replayFromCache(cached);
        }
        if (!chatRequestIdempotencySupport.tryMarkProcessing(userId, requestId)) {
            cached = chatRequestIdempotencySupport.findCompleted(userId, requestId);
            if (cached != null) {
                return replayFromCache(cached);
            }
            throw new AppException(ResponseCode.UN_ERROR.getCode(), "同 requestId 请求处理中，请稍后重试");
        }

        try {
            CreditDeductionResult creditResult;
            try {
                creditResult = applyCreditDeduction(token, effectiveCost, requestId);
            } catch (Exception e) {
                chatRequestIdempotencySupport.clearProcessing(userId, requestId);
                throw e;
            }

            try {
                String answer = PROVIDER_DEEPSEEK.equalsIgnoreCase(runtimeConfig.provider)
                        && StringUtils.isNotBlank(runtimeConfig.apiKey)
                        ? callDeepSeek(request.getMessage(), runtimeConfig.apiKey,
                                runtimeConfig.baseUrl, runtimeConfig.model)
                        : localFallback(request.getMessage());
                ChatbotAskResponseDTO responseDto = ChatbotAskResponseDTO.builder()
                        .intent("chat")
                        .toolName(PROVIDER_DEEPSEEK.equalsIgnoreCase(runtimeConfig.provider) ? PROVIDER_DEEPSEEK : "local")
                        .success(true)
                        .answer(answer)
                        .creditDeducted(creditResult.deducted)
                        .creditBalance(creditResult.balance)
                        .build();
                chatRequestIdempotencySupport.complete(userId, requestId, ChatRequestIdempotencySupport.CachedChatResponse.builder()
                        .answer(answer)
                        .toolName(responseDto.getToolName())
                        .success(true)
                        .creditDeducted(creditResult.deducted)
                        .creditBalance(creditResult.balance)
                        .build());
                return responseDto;
            } catch (Exception e) {
                log.error("AI call failed after credit deduction, refunding requestId:{}", creditResult.requestId, e);
                String failureAnswer = handleRefundAfterAiFailure(token, creditResult.requestId);
                String errorCode = ResponseCode.UN_ERROR.getCode();
                chatRequestIdempotencySupport.complete(userId, requestId, ChatRequestIdempotencySupport.CachedChatResponse.builder()
                        .answer(failureAnswer)
                        .toolName(PROVIDER_DEEPSEEK.equalsIgnoreCase(runtimeConfig.provider) ? PROVIDER_DEEPSEEK : "local")
                        .success(false)
                        .creditDeducted(creditResult.deducted)
                        .creditBalance(creditResult.balance)
                        .errorCode(errorCode)
                        .errorMessage(failureAnswer)
                        .build());
                throw new AppException(errorCode, failureAnswer);
            }
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            chatRequestIdempotencySupport.clearProcessing(userId, requestId);
            throw e;
        }
    }

    private String resolveUserIdForAsk(String token, int effectiveCost) {
        if (effectiveCost > 0) {
            String userId = chatTokenUserSupport.resolveUserId(token);
            if (StringUtils.isBlank(userId)) {
                throw new AppException(ResponseCode.Login.TOKEN_ERROR.getCode(), ResponseCode.Login.TOKEN_ERROR.getInfo());
            }
            return userId;
        }
        return StringUtils.defaultIfBlank(chatTokenUserSupport.resolveUserId(token), "__anonymous__");
    }

    private ChatbotAskResponseDTO replayFromCache(ChatRequestIdempotencySupport.CachedChatResponse cached) {
        if (!cached.isSuccess() && StringUtils.isNotBlank(cached.getErrorCode())) {
            throw new AppException(cached.getErrorCode(),
                    StringUtils.defaultIfBlank(cached.getErrorMessage(), cached.getAnswer()));
        }
        return toResponseDto(cached);
    }

    private ChatbotAskResponseDTO toResponseDto(ChatRequestIdempotencySupport.CachedChatResponse cached) {
        return ChatbotAskResponseDTO.builder()
                .intent("chat")
                .toolName(cached.getToolName())
                .success(cached.isSuccess())
                .answer(cached.getAnswer())
                .creditDeducted(cached.getCreditDeducted())
                .creditBalance(cached.getCreditBalance())
                .build();
    }

    private String handleRefundAfterAiFailure(String token, String requestId) {
        boolean refundSucceeded = false;
        String userId = chatTokenUserSupport.resolveUserId(token);
        try {
            marketCreditGatewayClient.refundCredit(token, requestId);
            if (StringUtils.isNotBlank(userId)) {
                chatCreditSessionSupport.markRefunded(userId, requestId);
            }
            refundSucceeded = true;
        } catch (Exception refundEx) {
            log.error("Refund failed after AI failure requestId:{}", requestId, refundEx);
            if (StringUtils.isNotBlank(userId)) {
                try {
                    marketCreditGatewayClient.markRefundPending(token, requestId);
                } catch (Exception pendingEx) {
                    chatCreditSessionSupport.markRefundPending(userId, requestId);
                }
            }
        }
        return resolveAiFailureUserMessage(refundSucceeded);
    }

    private String resolveAiFailureUserMessage(boolean refundSucceeded) {
        if (refundSucceeded) {
            return "AI 服务暂时不可用，已退还本次扣减的积分";
        }
        return "AI 服务暂时不可用。退款处理中，请稍后刷新余额查看是否已退还本次扣减的积分。";
    }

    /**
     * 按配置扣减本次对话积分：校验 Token、余额充足后扣款。
     * <p>effectiveCost 为 0 时跳过扣减，仅查询余额（未登录则返回 0）。</p>
     */
    private CreditDeductionResult applyCreditDeduction(String token, int effectiveCost, String requestId) {
        if (effectiveCost > 0 && StringUtils.isBlank(token)) {
            throw new AppException(ResponseCode.Login.TOKEN_ERROR.getCode(), ResponseCode.Login.TOKEN_ERROR.getInfo());
        }
        if (effectiveCost > 0) {
            BigDecimal balance = marketCreditGatewayClient.fetchCreditBalance(token);
            if (balance.compareTo(BigDecimal.valueOf(effectiveCost)) < 0) {
                throw new AppException(
                        ResponseCode.USER_CREDIT_ACCOUNT_NO_AVAILABLE_AMOUNT.getCode(),
                        "积分不足（需要 " + effectiveCost + " 积分，当前 " + balance + " 积分），请签到赚取积分或兑换后再试。");
            }
            BigDecimal newBalance = marketCreditGatewayClient.deductCredit(token, effectiveCost, requestId);
            return new CreditDeductionResult(requestId, BigDecimal.valueOf(effectiveCost), newBalance);
        }
        BigDecimal balance = StringUtils.isBlank(token)
                ? BigDecimal.ZERO
                : marketCreditGatewayClient.fetchCreditBalance(token);
        return new CreditDeductionResult(requestId, BigDecimal.ZERO, balance);
    }

    private ChatbotAskResponseDTO disabledResponse(String token) {
        BigDecimal balance = StringUtils.isBlank(token) ? BigDecimal.ZERO : marketCreditGatewayClient.fetchCreditBalance(token);
        return ChatbotAskResponseDTO.builder()
                .intent("chat")
                .toolName("disabled")
                .success(false)
                .answer("Chatbot 当前已在管理端关闭。")
                .creditDeducted(BigDecimal.ZERO)
                .creditBalance(balance)
                .build();
    }

    private int parseCostConfig(String val) {
        try {
            return Math.max(0, Integer.parseInt(val));
        } catch (NumberFormatException e) {
            return DEFAULT_COST_PER_ASK;
        }
    }

    private boolean isChargeableRemoteProvider(ChatbotRuntimeConfig runtimeConfig) {
        return runtimeConfig != null
                && PROVIDER_DEEPSEEK.equalsIgnoreCase(runtimeConfig.provider)
                && StringUtils.isNotBlank(runtimeConfig.apiKey);
    }

    @SuppressWarnings("unchecked")
    private String callDeepSeek(String userMessage, String apiKey, String baseUrl, String model) {
        String url = baseUrl.replaceAll("/$", "") + "/v1/chat/completions";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        Map<String, Object> message = new HashMap<>();
        message.put("role", "user");
        message.put(JSON_KEY_CONTENT, userMessage);

        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("messages", Collections.singletonList(message));
        body.put("stream", false);

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url, HttpMethod.POST, new HttpEntity<>(body, headers),
                new ParameterizedTypeReference<Map<String, Object>>() {});

        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.getBody().get("choices");
            if (choices != null && !choices.isEmpty()) {
                Map<String, Object> msg = (Map<String, Object>) choices.get(0).get("message");
                if (msg != null && msg.get(JSON_KEY_CONTENT) != null) {
                    return msg.get(JSON_KEY_CONTENT).toString();
                }
            }
        }
        throw new AppException(ResponseCode.UN_ERROR.getCode(), "DeepSeek 返回异常: " + response.getStatusCode());
    }

    private String localFallback(String userMessage) {
        if (StringUtils.isBlank(userMessage)) {
            return "你好！我是 Lucky Draw AI 平台的智能助手。有什么我可以帮你的吗？";
        }
        if (StringUtils.containsIgnoreCase(userMessage, "你好") || StringUtils.containsIgnoreCase(userMessage, "hi")) {
            return "你好！我是 Lucky Draw AI 平台的智能助手。你可以通过页面上的按钮进行抽奖、签到、查询积分和兑换商品。有什么问题尽管问我！";
        }
        if (StringUtils.containsIgnoreCase(userMessage, "功能") || StringUtils.containsIgnoreCase(userMessage, "能做什么")) {
            return "本平台提供以下功能：\n1. 轮盘抽奖 - 点击 GO 按钮参与\n2. 每日签到 - 获取积分奖励\n3. 积分兑换 - 兑换抽奖次数\n4. 活动查询 - 查看当前活动信息\n如需使用这些功能，请使用页面上的按钮操作。";
        }
        return "感谢你的提问。对于抽奖、签到、查询积分和兑换商品等操作，请使用页面上的按钮完成。如果你对平台有其他疑问，请随时问我！";
    }

    private static final class CreditDeductionResult {
        private final String requestId;
        private final BigDecimal deducted;
        private final BigDecimal balance;

        private CreditDeductionResult(String requestId, BigDecimal deducted, BigDecimal balance) {
            this.requestId = requestId;
            this.deducted = deducted;
            this.balance = balance;
        }
    }

    /** Immutable per-request view; a Nacos refresh cannot mix provider fields mid-call. */
    private static final class ChatbotRuntimeConfig {
        private final boolean enabled;
        private final String provider;
        private final String apiKey;
        private final String baseUrl;
        private final String model;
        private final String costPerAsk;

        private ChatbotRuntimeConfig(boolean enabled, String provider, String apiKey,
                                     String baseUrl, String model, String costPerAsk) {
            this.enabled = enabled;
            this.provider = provider;
            this.apiKey = apiKey;
            this.baseUrl = baseUrl;
            this.model = model;
            this.costPerAsk = costPerAsk;
        }

        private static ChatbotRuntimeConfig from(Map<String, String> values) {
            if (values == null) {
                values = Collections.emptyMap();
            }
            return new ChatbotRuntimeConfig(
                    "true".equalsIgnoreCase(values.getOrDefault("enabled", "true")),
                    values.getOrDefault("provider", DEFAULT_PROVIDER),
                    values.getOrDefault("apiKey", DEFAULT_API_KEY),
                    values.getOrDefault("baseUrl", DEFAULT_BASE_URL),
                    values.getOrDefault("model", DEFAULT_MODEL),
                    values.getOrDefault("costPerAsk", String.valueOf(DEFAULT_COST_PER_ASK)));
        }
    }
}

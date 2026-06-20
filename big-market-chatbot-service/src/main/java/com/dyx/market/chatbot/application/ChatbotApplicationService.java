package com.dyx.market.chatbot.application;

import com.dyx.market.chatbot.client.MarketCreditGatewayClient;
import com.dyx.market.management.config.PlatformConfigService;
import com.dyx.market.trigger.api.dto.ChatbotAskRequestDTO;
import com.dyx.market.trigger.api.dto.ChatbotAskResponseDTO;
import com.dyx.market.types.enums.ResponseCode;
import com.dyx.market.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.*;

@Slf4j
@Service
public class ChatbotApplicationService {

    private static final String CONFIG_NS_CHATBOT = "chatbot";
    private static final String PROVIDER_DEEPSEEK = "deepseek";
    private static final String JSON_KEY_CONTENT = "content";

    @Value("${chatbot.provider:local}")
    private String provider;

    @Value("${chatbot.deepseek.api-key:}")
    private String deepseekApiKey;

    @Value("${chatbot.deepseek.base-url:https://api.deepseek.com}")
    private String deepseekBaseUrl;

    @Value("${chatbot.deepseek.model:deepseek-chat}")
    private String deepseekModel;

    @Value("${chatbot.cost-per-ask:1}")
    private int costPerAsk;

    @Resource
    private PlatformConfigService platformConfigService;
    @Resource
    private MarketCreditGatewayClient marketCreditGatewayClient;
    @Resource
    private RestTemplate restTemplate;

    public ChatbotAskResponseDTO ask(ChatbotAskRequestDTO request, String token) {
        if (null == request || StringUtils.isBlank(request.getMessage())) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), ResponseCode.ILLEGAL_PARAMETER.getInfo());
        }
        if (!"true".equalsIgnoreCase(platformConfigService.getValue(CONFIG_NS_CHATBOT, "enabled", "true"))) {
            return disabledResponse(token);
        }

        int effectiveCost = parseCostConfig(
                platformConfigService.getValue(CONFIG_NS_CHATBOT, "costPerAsk", String.valueOf(costPerAsk)));
        CreditDeductionResult creditResult = applyCreditDeduction(token, effectiveCost, request);

        String effectiveProvider = platformConfigService.getValue(CONFIG_NS_CHATBOT, "provider", provider);
        String effectiveApiKey = platformConfigService.getValue(CONFIG_NS_CHATBOT, "apiKey", deepseekApiKey);
        try {
            String answer = PROVIDER_DEEPSEEK.equalsIgnoreCase(effectiveProvider) && StringUtils.isNotBlank(effectiveApiKey)
                    ? callDeepSeek(request.getMessage(), effectiveApiKey)
                    : localFallback(request.getMessage());
            return ChatbotAskResponseDTO.builder()
                    .intent("chat")
                    .toolName(PROVIDER_DEEPSEEK.equalsIgnoreCase(effectiveProvider) ? PROVIDER_DEEPSEEK : "local")
                    .success(true)
                    .answer(answer)
                    .creditDeducted(creditResult.deducted)
                    .creditBalance(creditResult.balance)
                    .build();
        } catch (Exception e) {
            log.error("AI call failed after credit deduction, refunding requestId:{}", creditResult.requestId, e);
            if (effectiveCost > 0) {
                marketCreditGatewayClient.refundCredit(token, effectiveCost, creditResult.requestId);
            }
            throw new AppException(ResponseCode.UN_ERROR.getCode(), "AI 服务暂时不可用，已退还本次扣减的积分");
        }
    }

    private CreditDeductionResult applyCreditDeduction(String token, int effectiveCost, ChatbotAskRequestDTO request) {
        if (effectiveCost > 0 && StringUtils.isBlank(token)) {
            throw new AppException(ResponseCode.Login.TOKEN_ERROR.getCode(), ResponseCode.Login.TOKEN_ERROR.getInfo());
        }
        String requestId = StringUtils.defaultIfBlank(request.getRequestId(), UUID.randomUUID().toString());
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
            return costPerAsk;
        }
    }

    @SuppressWarnings("unchecked")
    private String callDeepSeek(String userMessage, String apiKey) {
        String baseUrl = platformConfigService.getValue(CONFIG_NS_CHATBOT, "baseUrl", deepseekBaseUrl);
        String model = platformConfigService.getValue(CONFIG_NS_CHATBOT, "model", deepseekModel);
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
}

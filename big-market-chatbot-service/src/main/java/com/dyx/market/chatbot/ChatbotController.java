package com.dyx.market.chatbot;

import com.dyx.market.management.config.PlatformConfigService;
import com.dyx.market.trigger.api.dto.ChatbotAskRequestDTO;
import com.dyx.market.trigger.api.dto.ChatbotAskResponseDTO;
import com.dyx.market.trigger.api.response.Response;
import com.dyx.market.types.enums.ResponseCode;
import com.dyx.market.types.exception.AppException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@CrossOrigin("${app.config.cross-origin}")
@RequestMapping("/api/${app.config.api-version}/chatbot/")
public class ChatbotController {

    @Value("${chatbot.provider:local}")
    private String provider;

    @Value("${chatbot.deepseek.api-key:}")
    private String deepseekApiKey;

    @Value("${chatbot.deepseek.base-url:https://api.deepseek.com}")
    private String deepseekBaseUrl;

    @Value("${chatbot.deepseek.model:deepseek-chat}")
    private String deepseekModel;

    /** Cost in credits per successful AI ask. Default 1. Set to 0 for free mode. */
    @Value("${chatbot.cost-per-ask:1}")
    private int costPerAsk;

    /** Gateway base URL for calling market-service credit APIs */
    @Value("${chatbot.gateway-url:http://127.0.0.1:8080}")
    private String gatewayUrl;

    @Resource
    private PlatformConfigService platformConfigService;

    @Resource
    private RestTemplate restTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @RequestMapping(value = "ask", method = RequestMethod.POST)
    public Response<ChatbotAskResponseDTO> ask(@RequestBody ChatbotAskRequestDTO request,
                                                @RequestHeader(value = "Authorization", required = false) String token) {
        try {
            if (null == request || StringUtils.isBlank(request.getMessage())) {
                throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), ResponseCode.ILLEGAL_PARAMETER.getInfo());
            }

            if (!"true".equalsIgnoreCase(platformConfigService.getValue("chatbot", "enabled", "true"))) {
                BigDecimal balance = StringUtils.isBlank(token) ? BigDecimal.ZERO : fetchCreditBalance(token);
                return Response.<ChatbotAskResponseDTO>builder()
                        .code(ResponseCode.SUCCESS.getCode())
                        .info(ResponseCode.SUCCESS.getInfo())
                        .data(ChatbotAskResponseDTO.builder()
                                .intent("chat")
                                .toolName("disabled")
                                .success(false)
                                .answer("Chatbot 当前已在管理端关闭。")
                                .creditDeducted(BigDecimal.ZERO)
                                .creditBalance(balance)
                                .build())
                        .build();
            }

            int effectiveCost = parseCostConfig(platformConfigService.getValue("chatbot", "costPerAsk", String.valueOf(costPerAsk)));

            if (effectiveCost > 0 && StringUtils.isBlank(token)) {
                return Response.<ChatbotAskResponseDTO>builder()
                        .code(ResponseCode.Login.TOKEN_ERROR.getCode())
                        .info(ResponseCode.Login.TOKEN_ERROR.getInfo())
                        .data(ChatbotAskResponseDTO.builder()
                                .success(false)
                                .answer("登录已过期，请重新登录后再使用 AI 对话。")
                                .creditDeducted(BigDecimal.ZERO)
                                .creditBalance(BigDecimal.ZERO)
                                .build())
                        .build();
            }

            // Credit check (skip only in free mode)
            if (effectiveCost > 0) {
                BigDecimal balance = fetchCreditBalance(token);
                if (balance.compareTo(BigDecimal.valueOf(effectiveCost)) < 0) {
                    return Response.<ChatbotAskResponseDTO>builder()
                            .code(ResponseCode.USER_CREDIT_ACCOUNT_NO_AVAILABLE_AMOUNT.getCode())
                            .info("积分不足，签到或兑换后再试")
                            .data(ChatbotAskResponseDTO.builder()
                                    .success(false)
                                    .answer("积分不足（需要 " + effectiveCost + " 积分，当前 " + balance + " 积分），请签到赚取积分或兑换后再试。")
                                    .creditDeducted(BigDecimal.ZERO)
                                    .creditBalance(balance)
                                    .build())
                            .build();
                }
            }

            String message = request.getMessage();
            String requestId = StringUtils.defaultIfBlank(request.getRequestId(), UUID.randomUUID().toString());

            // Deduct credit BEFORE AI call, so the user is charged before we incur AI compute cost
            BigDecimal deducted = BigDecimal.ZERO;
            BigDecimal newBalance = BigDecimal.ZERO;
            if (effectiveCost > 0) {
                try {
                    newBalance = deductCredit(token, effectiveCost, requestId);
                    deducted = BigDecimal.valueOf(effectiveCost);
                } catch (Exception e) {
                    log.warn("AI Chat credit deduction failed", e);
                    return Response.<ChatbotAskResponseDTO>builder()
                            .code(ResponseCode.UN_ERROR.getCode())
                            .info("积分扣减失败，无法进行 AI 对话")
                            .data(ChatbotAskResponseDTO.builder()
                                    .success(false)
                                    .answer("积分扣减失败，无法进行 AI 对话。请确认积分充足后重试。")
                                    .creditDeducted(BigDecimal.ZERO)
                                    .creditBalance(fetchCreditBalance(token))
                                    .build())
                            .build();
                }
            } else if (StringUtils.isNotBlank(token)) {
                newBalance = fetchCreditBalance(token);
            }

            String effectiveProvider = platformConfigService.getValue("chatbot", "provider", provider);
            String effectiveApiKey   = platformConfigService.getValue("chatbot", "apiKey",   deepseekApiKey);

            String answer;
            try {
                if ("deepseek".equalsIgnoreCase(effectiveProvider) && StringUtils.isNotBlank(effectiveApiKey)) {
                    answer = callDeepSeek(message, effectiveApiKey);
                } else {
                    answer = localFallback(message);
                }
            } catch (Exception e) {
                log.error("AI call failed after credit deduction, refunding requestId:{}", requestId, e);
                if (effectiveCost > 0) {
                    refundCredit(token, effectiveCost, requestId);
                }
                return Response.<ChatbotAskResponseDTO>builder()
                        .code(ResponseCode.UN_ERROR.getCode())
                        .info("AI 服务暂时不可用")
                        .data(ChatbotAskResponseDTO.builder()
                                .success(false)
                                .answer("AI 服务暂时不可用，已退还本次扣减的积分。请稍后再试。")
                                .creditDeducted(BigDecimal.ZERO)
                                .creditBalance(fetchCreditBalance(token))
                                .build())
                        .build();
            }

            return Response.<ChatbotAskResponseDTO>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(ChatbotAskResponseDTO.builder()
                            .intent("chat")
                            .toolName("deepseek".equalsIgnoreCase(effectiveProvider) ? "deepseek" : "local")
                            .success(true)
                            .answer(answer)
                            .creditDeducted(deducted)
                            .creditBalance(newBalance)
                            .build())
                    .build();
        } catch (AppException e) {
            return Response.<ChatbotAskResponseDTO>builder()
                    .code(e.getCode())
                    .info(e.getInfo())
                    .data(ChatbotAskResponseDTO.builder().success(false).answer(e.getInfo()).build())
                    .build();
        } catch (Exception e) {
            log.error("Chatbot error", e);
            return Response.<ChatbotAskResponseDTO>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(ChatbotAskResponseDTO.builder().success(false).answer("助手暂时无法处理该请求，请稍后再试。").build())
                    .build();
        }
    }

    /** Fetch credit balance from market-service via gateway. */
    private BigDecimal fetchCreditBalance(String token) {
        if (StringUtils.isBlank(token)) return BigDecimal.ZERO;
        try {
            String url = gatewayUrl.replaceAll("/$", "") + "/api/v1/raffle/activity/query_user_credit_account_by_token";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", token);
            HttpEntity<String> entity = new HttpEntity<>("{}", headers);
            ResponseEntity<String> resp = restTemplate.postForEntity(url, entity, String.class);
            if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
                JsonNode root = objectMapper.readTree(resp.getBody());
                if ("0000".equals(root.path("code").asText()) && !root.path("data").isNull()) {
                    return new BigDecimal(root.path("data").asText());
                }
                throw new IllegalStateException(root.path("info").asText("查询积分失败"));
            }
        } catch (Exception e) {
            log.warn("Failed to fetch credit balance", e);
        }
        throw new IllegalStateException("查询积分失败");
    }

    /** Refund credit for a failed AI chat call. Best-effort — logs failures but doesn't throw. */
    private void refundCredit(String token, int amount, String originalRequestId) {
        String url = gatewayUrl.replaceAll("/$", "")
                + "/api/v1/raffle/activity/chat_credit_refund_by_token?amount=" + amount
                + "&originalRequestId=" + urlEncode(originalRequestId);
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", token);
            restTemplate.postForEntity(url, new HttpEntity<>("{}", headers), String.class);
        } catch (Exception e) {
            log.warn("Failed to refund credit for requestId:{} amount:{} — manual recovery may be needed",
                    originalRequestId, amount, e);
        }
    }

    /** Deduct credit for AI chat via market-service gateway. Returns new balance. */
    private BigDecimal deductCredit(String token, int amount, String requestId) {
        String url = gatewayUrl.replaceAll("/$", "")
                + "/api/v1/raffle/activity/chat_credit_deduct_by_token?amount=" + amount
                + "&requestId=" + urlEncode(requestId);
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", token);
            ResponseEntity<String> resp = restTemplate.postForEntity(url, new HttpEntity<>("{}", headers), String.class);
            if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
                JsonNode root = objectMapper.readTree(resp.getBody());
                if ("0000".equals(root.path("code").asText()) && !root.path("data").isNull()) {
                    return new BigDecimal(root.path("data").asText());
                }
                throw new IllegalStateException(root.path("info").asText("扣减积分失败"));
            }
        } catch (Exception e) {
            log.warn("Failed to deduct credit", e);
            throw new RuntimeException("Credit deduction failed", e);
        }
        throw new RuntimeException("Credit deduction failed");
    }

    private int parseCostConfig(String val) {
        try { return Math.max(0, Integer.parseInt(val)); } catch (NumberFormatException e) { return costPerAsk; }
    }

    private String urlEncode(String val) {
        try {
            return URLEncoder.encode(val, "UTF-8");
        } catch (Exception e) {
            return val;
        }
    }

    private String callDeepSeek(String userMessage, String apiKey) {
        String baseUrl = platformConfigService.getValue("chatbot", "baseUrl", deepseekBaseUrl);
        String model   = platformConfigService.getValue("chatbot", "model",   deepseekModel);
        String url     = baseUrl.replaceAll("/$", "") + "/v1/chat/completions";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        Map<String, Object> message = new HashMap<>();
        message.put("role", "user");
        message.put("content", userMessage);

        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("messages", Collections.singletonList(message));
        body.put("stream", false);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);

        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.getBody().get("choices");
            if (choices != null && !choices.isEmpty()) {
                Map<String, Object> choice = choices.get(0);
                Map<String, Object> msg = (Map<String, Object>) choice.get("message");
                if (msg != null && msg.get("content") != null) {
                    return msg.get("content").toString();
                }
            }
        }
        // Non-2xx or empty body — let caller handle refund
        throw new RuntimeException("DeepSeek returned unexpected response: " + response.getStatusCode());
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

}

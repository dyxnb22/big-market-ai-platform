package com.dyx.market.chatbot;

import com.dyx.market.management.config.PlatformConfigService;
import com.dyx.market.trigger.api.dto.ChatbotAskRequestDTO;
import com.dyx.market.trigger.api.dto.ChatbotAskResponseDTO;
import com.dyx.market.trigger.api.response.Response;
import com.dyx.market.types.enums.ResponseCode;
import com.dyx.market.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    @Resource
    private PlatformConfigService platformConfigService;

    @Resource
    private RestTemplate restTemplate;

    @RequestMapping(value = "ask", method = RequestMethod.POST)
    public Response<ChatbotAskResponseDTO> ask(@RequestBody ChatbotAskRequestDTO request) {
        try {
            if (null == request || StringUtils.isBlank(request.getMessage())) {
                throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), ResponseCode.ILLEGAL_PARAMETER.getInfo());
            }
            if (!"true".equalsIgnoreCase(platformConfigService.getValue("chatbot", "enabled", "true"))) {
                return Response.<ChatbotAskResponseDTO>builder()
                        .code(ResponseCode.SUCCESS.getCode())
                        .info(ResponseCode.SUCCESS.getInfo())
                        .data(ChatbotAskResponseDTO.builder()
                                .intent("chat")
                                .toolName("disabled")
                                .success(false)
                                .answer("Chatbot 当前已在管理端关闭。")
                                .build())
                        .build();
            }

            String message = request.getMessage();
            String answer;

            if ("deepseek".equalsIgnoreCase(provider) && StringUtils.isNotBlank(deepseekApiKey)) {
                answer = callDeepSeek(message);
            } else {
                answer = localFallback(message);
            }

            return Response.<ChatbotAskResponseDTO>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(ChatbotAskResponseDTO.builder()
                            .intent("chat")
                            .toolName("deepseek".equalsIgnoreCase(provider) ? "deepseek" : "local")
                            .success(true)
                            .answer(answer)
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

    private String callDeepSeek(String userMessage) {
        try {
            String url = deepseekBaseUrl.replaceAll("/$", "") + "/v1/chat/completions";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(deepseekApiKey);

            Map<String, Object> message = new HashMap<>();
            message.put("role", "user");
            message.put("content", userMessage);

            Map<String, Object> body = new HashMap<>();
            body.put("model", deepseekModel);
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
            log.warn("DeepSeek returned unexpected response: {}", response);
            return localFallback(userMessage);
        } catch (Exception e) {
            log.error("DeepSeek API call failed", e);
            return "抱歉，我暂时无法连接到 AI 服务。你可以继续在页面上使用抽奖、签到、积分兑换等功能。";
        }
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

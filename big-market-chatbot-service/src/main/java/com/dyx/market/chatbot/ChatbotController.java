package com.dyx.market.chatbot;

import com.dyx.market.chatbot.application.ChatbotApplicationService;
import com.dyx.market.chatbot.config.ChatbotExceptionHandler;
import com.dyx.market.trigger.api.dto.ChatbotAskRequestDTO;
import com.dyx.market.trigger.api.dto.ChatbotAskResponseDTO;
import com.dyx.market.trigger.api.response.Response;
import com.dyx.market.types.enums.ResponseCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/**
 * Chatbot HTTP 接口：AI 对话问答。
 *
 * <p>路径前缀 {@code /api/{api-version}/chatbot/}，业务编排见 {@link ChatbotApplicationService}。</p>
 */
@Slf4j
@RestController
@CrossOrigin("${app.config.cross-origin}")
@RequestMapping("/api/${app.config.api-version}/chatbot/")
public class ChatbotController {

    @Resource
    private ChatbotApplicationService chatbotApplicationService;

    /**
     * AI 对话入口：校验参数后委托应用服务，异常时由 {@link ChatbotExceptionHandler} 统一处理。
     * <p>通过请求属性标记 /ask 端点，避免反向代理改写 URI 后异常响应格式不一致。</p>
     */
    @PostMapping("ask")
    public Response<ChatbotAskResponseDTO> ask(@Valid @RequestBody ChatbotAskRequestDTO request,
                                               @RequestHeader(value = "Authorization", required = false) String token,
                                               HttpServletRequest httpRequest) {
        // 标记当前请求，使异常处理器即使在反向代理改写 URI 后，也能在错误响应体中使用
        // ChatbotAskResponseDTO 结构。
        httpRequest.setAttribute(ChatbotExceptionHandler.ATTR_ASK_ENDPOINT, Boolean.TRUE);
        ChatbotAskResponseDTO data = chatbotApplicationService.ask(request, token);
        return Response.<ChatbotAskResponseDTO>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(data)
                .build();
    }
}

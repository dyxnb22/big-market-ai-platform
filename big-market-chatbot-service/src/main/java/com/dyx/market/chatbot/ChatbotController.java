package com.dyx.market.chatbot;

import com.dyx.market.chatbot.application.ChatbotApplicationService;
import com.dyx.market.chatbot.config.ChatbotExceptionHandler;
import com.dyx.market.trigger.api.dto.ChatbotAskRequestDTO;
import com.dyx.market.trigger.api.dto.ChatbotAskResponseDTO;
import com.dyx.market.trigger.api.response.Response;
import com.dyx.market.types.enums.ResponseCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

@Slf4j
@RestController
@CrossOrigin("${app.config.cross-origin}")
@RequestMapping("/api/${app.config.api-version}/chatbot/")
public class ChatbotController {

    @Resource
    private ChatbotApplicationService chatbotApplicationService;

    @PostMapping("ask")
    public Response<ChatbotAskResponseDTO> ask(@RequestBody ChatbotAskRequestDTO request,
                                               @RequestHeader(value = "Authorization", required = false) String token,
                                               HttpServletRequest httpRequest) {
        // Mark this request so the exception handler knows to include ChatbotAskResponseDTO
        // in the error body regardless of URI rewriting by a reverse proxy.
        httpRequest.setAttribute(ChatbotExceptionHandler.ATTR_ASK_ENDPOINT, Boolean.TRUE);
        ChatbotAskResponseDTO data = chatbotApplicationService.ask(request, token);
        return Response.<ChatbotAskResponseDTO>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(data)
                .build();
    }
}

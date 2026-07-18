package com.dyx.market.admin;

import java.io.IOException;
import java.util.List;

import jakarta.annotation.Resource;

import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.dyx.market.management.config.PlatformConfigService;
import com.dyx.market.trigger.api.dto.ActivityDisplayConfigResponseDTO;
import com.dyx.market.trigger.api.dto.AdminConfigRequestDTO;
import com.dyx.market.trigger.api.dto.AdminConfigResponseDTO;
import com.dyx.market.trigger.api.response.Response;
import com.dyx.market.types.enums.ResponseCode;
import com.dyx.market.types.exception.AppException;

/**
 * 管理端平台配置 HTTP 接口：列表、查询、保存、删除。
 * <p>
 * 路径前缀 {@code /api/{api-version}/admin/config/}，由 {@link PlatformConfigService} 提供数据。
 */
@RestController
@CrossOrigin("${app.config.cross-origin}")
@RequestMapping("/api/${app.config.api-version}/admin/config/")
public class AdminConfigController {

    private static final String DEFAULT_ACTIVITY_TITLE = "幸运轮盘活动";
    private static final String DEFAULT_ACTIVITY_COPY  = "登录参与抽奖，AI 帮你解读活动权益。";
    /** 演示栈默认上架，避免用户端抽奖按钮长期 disabled。 */
    private static final String DEFAULT_ACTIVITY_STATE = "online";

    @Resource
    private PlatformConfigService platformConfigService;

    private void requireValidRequest(AdminConfigRequestDTO request) {
        if (null == request || StringUtils.isBlank(request.getNamespace()) || StringUtils.isBlank(request.getConfigKey())) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), ResponseCode.ILLEGAL_PARAMETER.getInfo());
        }
    }

    @GetMapping("list")
    public Response<List<AdminConfigResponseDTO>> list(@RequestParam(required = false) String namespace) {
        return Response.<List<AdminConfigResponseDTO>>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(platformConfigService.list(namespace))
                .build();
    }

    @GetMapping("get")
    public Response<AdminConfigResponseDTO> get(@RequestParam String namespace, @RequestParam String configKey) {
        if (StringUtils.isBlank(namespace) || StringUtils.isBlank(configKey)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), ResponseCode.ILLEGAL_PARAMETER.getInfo());
        }
        return Response.<AdminConfigResponseDTO>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(platformConfigService.get(namespace, configKey))
                .build();
    }

    @PostMapping("save")
    public Response<AdminConfigResponseDTO> save(@RequestBody AdminConfigRequestDTO request) throws IOException {
        requireValidRequest(request);
        return Response.<AdminConfigResponseDTO>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(platformConfigService.save(request))
                .build();
    }

    /**
     * 用户端公开只读：活动展示配置与 AI 开关（无需管理员鉴权）。
     */
    @GetMapping("public/display")
    public Response<ActivityDisplayConfigResponseDTO> publicDisplay(@RequestParam long activityId) {
        String ns = "activity." + activityId;
        String title = platformConfigService.getValue(ns, "title", DEFAULT_ACTIVITY_TITLE);
        String copy  = platformConfigService.getValue(ns, "copy",  DEFAULT_ACTIVITY_COPY);
        String state = platformConfigService.getValue(ns, "state", DEFAULT_ACTIVITY_STATE);
        boolean chatbotEnabled = !"false".equalsIgnoreCase(
                platformConfigService.getValue("chatbot", "enabled", "true"));
        return Response.<ActivityDisplayConfigResponseDTO>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(ActivityDisplayConfigResponseDTO.builder()
                        .activityId(activityId)
                        .title(title)
                        .copy(copy)
                        .state(state)
                        .chatbotEnabled(chatbotEnabled)
                        .build())
                .build();
    }

    @PostMapping("delete")
    public Response<Boolean> delete(@RequestBody AdminConfigRequestDTO request) throws IOException {
        requireValidRequest(request);
        platformConfigService.delete(request.getNamespace(), request.getConfigKey());
        return Response.<Boolean>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(true)
                .build();
    }

}

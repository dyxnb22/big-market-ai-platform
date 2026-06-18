package com.dyx.market.admin;

import com.dyx.market.management.config.PlatformConfigService;
import com.dyx.market.trigger.api.dto.AdminConfigRequestDTO;
import com.dyx.market.trigger.api.dto.AdminConfigResponseDTO;
import com.dyx.market.trigger.api.response.Response;
import com.dyx.market.types.enums.ResponseCode;
import com.dyx.market.types.exception.AppException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

/**
 * 管理端平台配置 HTTP 接口：列表、查询、保存、删除。
 * <p>
 * 路径前缀 {@code /api/{api-version}/admin/config/}，由 {@link PlatformConfigService} 提供数据。
 */
@RestController
@CrossOrigin("${app.config.cross-origin}")
@RequestMapping("/api/${app.config.api-version}/admin/config/")
public class AdminConfigController {

    @Resource
    private PlatformConfigService platformConfigService;

    @RequestMapping(value = "list", method = RequestMethod.GET)
    public Response<List<AdminConfigResponseDTO>> list(@RequestParam(required = false) String namespace) {
        return Response.<List<AdminConfigResponseDTO>>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(platformConfigService.list(namespace))
                .build();
    }

    @RequestMapping(value = "get", method = RequestMethod.GET)
    public Response<AdminConfigResponseDTO> get(@RequestParam String namespace, @RequestParam String configKey) {
        if (StringUtils.isBlank(namespace) || StringUtils.isBlank(configKey)) {
            return Response.<AdminConfigResponseDTO>builder()
                    .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                    .info(ResponseCode.ILLEGAL_PARAMETER.getInfo())
                    .build();
        }
        return Response.<AdminConfigResponseDTO>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(platformConfigService.get(namespace, configKey))
                .build();
    }

    @RequestMapping(value = "save", method = RequestMethod.POST)
    public Response<AdminConfigResponseDTO> save(@RequestBody AdminConfigRequestDTO request) {
        try {
            if (null == request || StringUtils.isBlank(request.getNamespace()) || StringUtils.isBlank(request.getConfigKey())) {
                throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), ResponseCode.ILLEGAL_PARAMETER.getInfo());
            }
            return Response.<AdminConfigResponseDTO>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(platformConfigService.save(request))
                    .build();
        } catch (AppException e) {
            return Response.<AdminConfigResponseDTO>builder().code(e.getCode()).info(e.getInfo()).build();
        } catch (Exception e) {
            return Response.<AdminConfigResponseDTO>builder().code(ResponseCode.UN_ERROR.getCode()).info(ResponseCode.UN_ERROR.getInfo()).build();
        }
    }

    @RequestMapping(value = "delete", method = RequestMethod.POST)
    public Response<Boolean> delete(@RequestBody AdminConfigRequestDTO request) {
        try {
            if (null == request || StringUtils.isBlank(request.getNamespace()) || StringUtils.isBlank(request.getConfigKey())) {
                throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), ResponseCode.ILLEGAL_PARAMETER.getInfo());
            }
            platformConfigService.delete(request.getNamespace(), request.getConfigKey());
            return Response.<Boolean>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(true)
                    .build();
        } catch (AppException e) {
            return Response.<Boolean>builder().code(e.getCode()).info(e.getInfo()).build();
        } catch (Exception e) {
            return Response.<Boolean>builder().code(ResponseCode.UN_ERROR.getCode()).info(ResponseCode.UN_ERROR.getInfo()).build();
        }
    }

}

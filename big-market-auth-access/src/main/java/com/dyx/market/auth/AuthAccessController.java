package com.dyx.market.auth;

import com.dyx.market.domain.auth.service.IAuthService;
import com.dyx.market.trigger.api.dto.LoginRequestDTO;
import com.dyx.market.trigger.api.dto.LoginResponseDTO;
import com.dyx.market.trigger.api.response.Response;
import com.dyx.market.types.enums.ResponseCode;
import com.dyx.market.types.exception.AppException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
@CrossOrigin("${app.config.cross-origin}")
@RequestMapping("/api/${app.config.api-version}/auth/")
public class AuthAccessController {

    private static final long TOKEN_TTL_SECONDS = 24 * 60 * 60L;

    @Resource
    private IAuthService authService;

    @RequestMapping(value = "login", method = RequestMethod.POST)
    public Response<LoginResponseDTO> login(@RequestBody LoginRequestDTO request) {
        try {
            if (null == request || StringUtils.isBlank(request.getUserId())) {
                throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), ResponseCode.ILLEGAL_PARAMETER.getInfo());
            }
            String token = authService.createToken(request.getUserId());
            return Response.<LoginResponseDTO>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(LoginResponseDTO.builder()
                            .userId(request.getUserId())
                            .token(token)
                            .expiresIn(TOKEN_TTL_SECONDS)
                            .build())
                    .build();
        } catch (AppException e) {
            return Response.<LoginResponseDTO>builder().code(e.getCode()).info(e.getInfo()).build();
        } catch (Exception e) {
            return Response.<LoginResponseDTO>builder().code(ResponseCode.UN_ERROR.getCode()).info(ResponseCode.UN_ERROR.getInfo()).build();
        }
    }

    @RequestMapping(value = "verify", method = RequestMethod.GET)
    public Response<String> verify(@RequestHeader("Authorization") String token) {
        if (!authService.checkToken(token)) {
            return Response.<String>builder()
                    .code(ResponseCode.Login.TOKEN_ERROR.getCode())
                    .info(ResponseCode.Login.TOKEN_ERROR.getInfo())
                    .build();
        }
        return Response.<String>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(authService.openid(token))
                .build();
    }

}

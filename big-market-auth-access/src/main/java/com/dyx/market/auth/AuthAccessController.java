package com.dyx.market.auth;

import com.dyx.market.domain.auth.service.IAuthService;
import com.dyx.market.trigger.api.dto.LoginRequestDTO;
import com.dyx.market.trigger.api.dto.LoginResponseDTO;
import com.dyx.market.trigger.api.response.Response;
import com.dyx.market.types.enums.ResponseCode;
import com.dyx.market.types.exception.AppException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@CrossOrigin("${app.config.cross-origin}")
@RequestMapping("/api/${app.config.api-version}/auth/")
public class AuthAccessController {

    private static final long TOKEN_TTL_SECONDS = 24 * 60 * 60L;

    @Resource
    private IAuthService authService;

    /**
     * Dev/test-only credential source for the local demo frontend.
     * Format: userId:password,userId2:password2
     */
    @Value("${app.auth.dev-users:xiaofuge:demo,admin:admin}")
    private String devUsers;

    @RequestMapping(value = "login", method = RequestMethod.POST)
    public Response<LoginResponseDTO> login(@RequestBody LoginRequestDTO request) {
        try {
            if (null == request || StringUtils.isBlank(request.getUserId()) || StringUtils.isBlank(request.getPassword())) {
                throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), ResponseCode.ILLEGAL_PARAMETER.getInfo());
            }
            if (!isValidCredential(request.getUserId().trim(), request.getPassword())) {
                return Response.<LoginResponseDTO>builder()
                        .code(ResponseCode.Login.TOKEN_ERROR.getCode())
                        .info("账号或密码错误")
                        .build();
            }
            String token = authService.createToken(request.getUserId().trim());
            return Response.<LoginResponseDTO>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(LoginResponseDTO.builder()
                            .userId(request.getUserId().trim())
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

    private boolean isValidCredential(String userId, String password) {
        Map<String, String> users = Arrays.stream(StringUtils.defaultString(devUsers).split(","))
                .map(String::trim)
                .filter(StringUtils::isNotBlank)
                .map(item -> item.split(":", 2))
                .filter(parts -> parts.length == 2 && StringUtils.isNotBlank(parts[0]))
                .collect(Collectors.toMap(parts -> parts[0].trim(), parts -> parts[1], (left, right) -> right));
        return password.equals(users.get(userId));
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

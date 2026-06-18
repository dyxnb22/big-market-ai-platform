package com.dyx.market.auth;

import com.dyx.market.domain.auth.service.IAuthService;
import com.dyx.market.domain.auth.service.ITokenRevocationService;
import com.dyx.market.trigger.api.dto.LoginRequestDTO;
import com.dyx.market.trigger.api.dto.LoginResponseDTO;
import com.dyx.market.trigger.api.response.Response;
import com.dyx.market.types.enums.ResponseCode;
import com.dyx.market.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 认证 HTTP 接口：登录、Token 校验、登出（吊销）。
 * <p>
 * 路径前缀 {@code /api/{api-version}/auth/}。
 */
@Slf4j
@RestController
@CrossOrigin("${app.config.cross-origin}")
@RequestMapping("/api/${app.config.api-version}/auth/")
public class AuthAccessController {

    private static final long TOKEN_TTL_SECONDS = 24 * 60 * 60L;

    @Resource
    private IAuthService authService;

    /** 由共享 {@link com.dyx.market.domain.auth.config.TokenRevocationConfig} 注入。 */
    @Resource
    private ITokenRevocationService tokenRevocationService;

    /**
     * 开发/测试用凭证来源，供本地 Demo 前端登录。
     * 格式：userId:password,userId2:password2，绑定 {@code app.auth.dev-users}。
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
                log.warn("[AuthAccessController] login denied — invalid credentials userId:{}", request.getUserId().trim());
                return Response.<LoginResponseDTO>builder()
                        .code(ResponseCode.Login.TOKEN_ERROR.getCode())
                        .info("账号或密码错误")
                        .build();
            }
            String token = authService.createToken(request.getUserId().trim());
            String jti = authService.extractJti(token);
            log.info("[AuthAccessController] login success userId:{} jti:{}", request.getUserId().trim(), jti);
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
            log.error("[AuthAccessController] login error", e);
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
            log.warn("[AuthAccessController] verify failed — token rejected");
            return Response.<String>builder()
                    .code(ResponseCode.Login.TOKEN_ERROR.getCode())
                    .info(ResponseCode.Login.TOKEN_ERROR.getInfo())
                    .build();
        }
        String openid = authService.openid(token);
        log.debug("[AuthAccessController] verify success openid:{}", openid);
        return Response.<String>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(openid)
                .build();
    }

    /**
     * 登出 / 吊销：将 Token 的 jti 加入黑名单，后续 verify() 将拒绝该 Token。
     * <p>
     * 幂等——重复吊销已吊销的 Token 仍返回 SUCCESS。
     */
    @RequestMapping(value = "logout", method = RequestMethod.POST)
    public Response<String> logout(@RequestHeader("Authorization") String token) {
        try {
            String jti = authService.extractJti(token);
            if (jti == null) {
                return Response.<String>builder()
                        .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                        .info("Unable to extract token identifier — token may be malformed")
                        .build();
            }
            long expiresAt = authService.extractExpiration(token);
            if (expiresAt > 0) {
                tokenRevocationService.revoke(jti, expiresAt);
            } else {
                // Token 无过期时间时，以较长 TTL 吊销作为兜底
                tokenRevocationService.revoke(jti, System.currentTimeMillis() + TOKEN_TTL_SECONDS * 1000);
            }
            log.info("[AuthAccessController] token revoked jti:{}", jti);
            return Response.<String>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info("Token revoked successfully")
                    .build();
        } catch (Exception e) {
            log.error("[AuthAccessController] logout failed", e);
            return Response.<String>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info("Token revocation failed: " + e.getMessage())
                    .build();
        }
    }

}

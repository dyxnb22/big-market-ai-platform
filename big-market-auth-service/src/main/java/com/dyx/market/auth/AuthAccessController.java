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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@CrossOrigin("${app.config.cross-origin}")
@RequestMapping("/api/${app.config.api-version}/auth/")
public class AuthAccessController {

    private static final long TOKEN_TTL_SECONDS = 24 * 60 * 60L;

    @Resource
    private IAuthService authService;

    /** Optional — present only when TokenRevocationConfig provides a bean. */
    @Autowired(required = false)
    private ITokenRevocationService tokenRevocationService;

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
     * Revoke/logout: adds the token's jti to the blacklist so subsequent
     * verify() calls will reject it.
     *
     * Idempotent — revoking an already-revoked or invalid token returns SUCCESS.
     * When no revocation service is configured (monolith fallback),
     * returns a NOT_IMPLEMENTED response.
     */
    @RequestMapping(value = "logout", method = RequestMethod.POST)
    public Response<String> logout(@RequestHeader("Authorization") String token) {
        if (tokenRevocationService == null) {
            log.warn("[AuthAccessController] logout called but no ITokenRevocationService is configured");
            return Response.<String>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info("Token revocation is not available in this deployment mode")
                    .build();
        }
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
                // Token has no expiration; revoke with a long TTL as safety net
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

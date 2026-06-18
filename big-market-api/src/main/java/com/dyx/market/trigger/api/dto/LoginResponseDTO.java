package com.dyx.market.trigger.api.dto;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

/**
 * 用户登录应答对象。
 */
@Data
@Builder
public class LoginResponseDTO implements Serializable {

    /** 用户 ID */
    private String userId;

    /** 访问令牌 */
    private String token;

    /** 令牌有效期（秒） */
    private Long expiresIn;

}

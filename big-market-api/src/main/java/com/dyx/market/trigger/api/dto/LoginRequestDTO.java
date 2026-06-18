package com.dyx.market.trigger.api.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 用户登录请求对象。
 */
@Data
public class LoginRequestDTO implements Serializable {

    /** 用户 ID */
    private String userId;

    /** 登录密码 */
    private String password;

}

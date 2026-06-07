package com.dyx.market.trigger.api.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class LoginRequestDTO implements Serializable {

    private String userId;

    private String password;

}

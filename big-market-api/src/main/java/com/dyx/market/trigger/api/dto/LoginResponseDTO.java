package com.dyx.market.trigger.api.dto;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

@Data
@Builder
public class LoginResponseDTO implements Serializable {

    private String userId;

    private String token;

    private Long expiresIn;

}

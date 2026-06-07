package com.dyx.market.trigger.api.dto;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

@Data
@Builder
public class AdminConfigResponseDTO implements Serializable {

    private String namespace;

    private String configKey;

    private String configValue;

    private String description;

    private Long updateTime;

}

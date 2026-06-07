package com.dyx.market.trigger.api.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class AdminConfigRequestDTO implements Serializable {

    private String namespace;

    private String configKey;

    private String configValue;

    private String description;

}

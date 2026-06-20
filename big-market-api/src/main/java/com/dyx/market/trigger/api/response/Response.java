package com.dyx.market.trigger.api.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 统一 API 响应包装：{@code code} 业务码、{@code info} 提示信息、{@code data} 载荷。
 * <p>
 * 与 {@link com.dyx.market.types.common.Response} 结构一致；api 模块独立发布时保留本类型。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Response<T> implements Serializable {

    private String code;
    private String info;
    @SuppressWarnings("java:S1948")
    private T data;

}

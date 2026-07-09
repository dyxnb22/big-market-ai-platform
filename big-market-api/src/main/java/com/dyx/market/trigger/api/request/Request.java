package com.dyx.market.trigger.api.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 跨应用 RPC/HTTP 统一请求包装：{@code appId}、{@code appToken} 鉴权，{@code data} 业务载荷。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Request<T> implements Serializable {

    /** 请求应用ID */
    private String appId;
    /** 请求应用Token */
    private String appToken;
    /** 请求对象 */
    private T data;

}

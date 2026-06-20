package com.dyx.market.starter.dubbo.auth;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 服务间 Dubbo 调用共享令牌；{@code enforce=false} 时不过滤（本地学习默认）。
 */
@Data
@ConfigurationProperties(prefix = "app.internal-rpc", ignoreInvalidFields = true)
public class InternalRpcAuthProperties {

    private String token = "big-market-internal-dev";
    private boolean enforce = false;
}

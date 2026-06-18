package com.dyx.market.gateway.fallback;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * 熔断降级响应：下游不可用或 Resilience4j 熔断打开时由网关内部转发到此。
 * <p>
 * 路由配置示例：{@code fallbackUri: forward:/fallback/auth-service}
 * <p>
 * 错误码 {@code 0007} 对应 big-market-types 中的 {@code ResponseCode.GATEWAY_ERROR}。
 */
@RestController
public class FallbackController {

    /**
     * {@code service} 为路由 id 后缀（如 auth-service），当前各服务返回相同结构。
     */
    @RequestMapping("/fallback/{service}")
    public Mono<Map<String, Object>> fallback() {
        Map<String, Object> body = new HashMap<>();
        body.put("code", "0007");
        body.put("info", "网关接口调用失败");
        body.put("data", null);
        return Mono.just(body);
    }
}

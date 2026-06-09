package com.dyx.market.gateway.fallback;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * Returns a stable JSON error when a downstream circuit breaker trips.
 * Code 0007 matches ResponseCode.GATEWAY_ERROR in big-market-types.
 */
@RestController
public class FallbackController {

    @RequestMapping("/fallback/{service}")
    public Mono<Map<String, Object>> fallback() {
        Map<String, Object> body = new HashMap<>();
        body.put("code", "0007");
        body.put("info", "网关接口调用失败");
        body.put("data", null);
        return Mono.just(body);
    }
}

package com.dyx.market.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * 全链路追踪：为每个请求注入 {@code X-Trace-Id}。
 * <p>
 * 请求无该头时生成 UUID；写入转发给下游的请求头，并在响应头中回显，
 * 便于在网关与各微服务日志中关联同一次调用。
 */
@Component
public class TraceIdGlobalFilter implements GlobalFilter, Ordered {

    private static final String TRACE_HEADER = "X-Trace-Id";

    /**
     * 尽量靠前执行，确保后续路由过滤器、下游转发都能拿到 trace id。
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String traceId = exchange.getRequest().getHeaders().getFirst(TRACE_HEADER);
        if (traceId == null || traceId.isEmpty()) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }
        final String tid = traceId;

        // 不可变请求需 mutate 后替换 exchange 中的 request
        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .header(TRACE_HEADER, tid)
                .build();

        ServerWebExchange mutatedExchange = exchange.mutate()
                .request(mutatedRequest)
                .build();

        // 响应提交前把 trace id 写回，客户端也能拿到
        mutatedExchange.getResponse().beforeCommit(() -> {
            mutatedExchange.getResponse().getHeaders().set(TRACE_HEADER, tid);
            return Mono.empty();
        });

        return chain.filter(mutatedExchange);
    }
}

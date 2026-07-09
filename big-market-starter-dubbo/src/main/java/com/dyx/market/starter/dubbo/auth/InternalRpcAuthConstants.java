package com.dyx.market.starter.dubbo.auth;

/**
 * Dubbo 内部 RPC 鉴权常量，定义令牌在 RpcContext 中的 attachment 键名。
 */
public final class InternalRpcAuthConstants {

    public static final String ATTACHMENT_KEY = "internalRpcToken";

    private InternalRpcAuthConstants() {
    }
}

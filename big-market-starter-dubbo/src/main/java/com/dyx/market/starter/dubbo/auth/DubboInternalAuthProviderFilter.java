package com.dyx.market.starter.dubbo.auth;

import org.apache.commons.lang3.StringUtils;
import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.rpc.Filter;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcContext;
import org.apache.dubbo.rpc.RpcException;

@Activate(group = CommonConstants.PROVIDER, order = -9000)
public class DubboInternalAuthProviderFilter implements Filter {

    private static volatile InternalRpcAuthProperties properties;

    public static void configure(InternalRpcAuthProperties authProperties) {
        properties = authProperties;
    }

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        InternalRpcAuthProperties cfg = properties;
        if (cfg == null || !cfg.isEnforce()) {
            return invoker.invoke(invocation);
        }
        String expected = cfg.getToken();
        String actual = RpcContext.getServiceContext().getAttachment(InternalRpcAuthConstants.ATTACHMENT_KEY);
        if (StringUtils.isBlank(expected) || !expected.equals(actual)) {
            throw new RpcException(RpcException.FORBIDDEN_EXCEPTION, "Internal RPC auth failed");
        }
        return invoker.invoke(invocation);
    }
}

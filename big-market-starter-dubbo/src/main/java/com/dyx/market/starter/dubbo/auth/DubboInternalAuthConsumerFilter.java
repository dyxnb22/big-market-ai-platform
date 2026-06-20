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

@Activate(group = CommonConstants.CONSUMER, order = -9000)
public class DubboInternalAuthConsumerFilter implements Filter {

    private static volatile InternalRpcAuthProperties properties;

    public static void configure(InternalRpcAuthProperties authProperties) {
        properties = authProperties;
    }

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        InternalRpcAuthProperties cfg = properties;
        if (cfg != null && StringUtils.isNotBlank(cfg.getToken())) {
            RpcContext.getClientAttachment().setAttachment(
                    InternalRpcAuthConstants.ATTACHMENT_KEY, cfg.getToken());
        }
        return invoker.invoke(invocation);
    }
}

package com.dyx.market.trigger.adapter;

import com.dyx.market.domain.rebate.model.entity.BehaviorEntity;

import java.util.List;

/**
 * 返利订单创建路由适配器契约。
 * <p>
 * 根据 {@code rebate.service.remote-create-order.enabled} 将创建请求路由到本地领域服务（默认），
 * 或经 Dubbo 访问 big-market-rebate-service。
 * 默认走本地领域服务（flag=false）；仅当开关为 true 时启用远程 Dubbo 调用。
 * 启用远程路径前须满足：
 * <ul>
 *   <li>重复 IRebateService 提供者风险已消除（market-service trigger.rpc 默认提供者已禁用）</li>
 *   <li>共享任务 outbox 归属已明确</li>
 *   <li>本地验证已通过</li>
 * </ul>
 */
public interface IRebateOrderAdapter {

    List<String> createOrder(BehaviorEntity behaviorEntity);

}

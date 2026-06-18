package com.dyx.market.trigger.adapter;

/**
 * 返利订单读查询路由适配器契约。
 * <p>
 * 根据 {@code rebate.service.remote-read.enabled} 将查询路由到本地领域服务（默认），
 * 或经 Dubbo 访问 big-market-rebate-service。
 * 默认走本地领域服务（flag=false）；仅当开关为 true 时启用远程 Dubbo 调用。
 * 启用远程路径前须满足：
 * <ul>
 *   <li>重复 IRebateService 提供者风险已消除（默认 RPC 提供者已禁用）</li>
 *   <li>big-market-rebate-service 本地验证已通过</li>
 * </ul>
 */
public interface IRebateReadAdapter {

    boolean isCalendarSignRebate(String userId, String outBusinessNo);

}

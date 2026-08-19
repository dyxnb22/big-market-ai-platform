package com.dyx.market.domain.activity.adapter.port;

/**
 * 远程写结果对账 Outbox 端口。
 *
 * <p>RPC 结果未知时，调用方提供原始业务幂等键和请求载荷；基础设施实现会将任务持久化
 * 到用户所在分片，供对账 Job 处理。领域层不能直接依赖 DAO 或基础设施实现。</p>
 */
public interface IPendingRemoteWritePort {

    /**
     * 按指定用户分片写入待对账任务。
     *
     * @return true 表示新任务已入队，false 表示相同业务幂等键已有记录
     */
    boolean enqueue(String outBusinessNo, String operation, Object payload, String userId);

    /**
     * 写入待对账任务；由实现根据载荷或当前上下文确定分片。
     *
     * @return true 表示新任务已入队，false 表示相同业务幂等键已有记录
     */
    boolean enqueue(String outBusinessNo, String operation, Object payload);

}

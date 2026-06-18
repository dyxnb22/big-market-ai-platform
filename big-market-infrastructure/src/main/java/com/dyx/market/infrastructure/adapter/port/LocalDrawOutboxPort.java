package com.dyx.market.infrastructure.adapter.port;

import com.dyx.market.domain.activity.adapter.port.IDrawOutboxPort;
import com.dyx.market.domain.activity.model.event.DrawOutboxEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * {@link IDrawOutboxPort} 的本地（进程内）空操作实现。
 *
 * <p>本地路径无需额外写入 Outbox：{@code AwardRepository.saveUserAwardRecord}
 * 已在与 {@code user_award_record} 同一数据库事务内写入任务 Outbox 行。
 * 持久性由既有本地事务保证；本适配器仅记录 Saga 步骤日志以供可观测性使用。</p>
 *
 * <p>激活条件：默认通过 {@code @ConditionalOnMissingBean} 生效。当
 * {@code activity.service.draw-outbox.enabled=true} 时，
 * 配置的远程 {@code ActivityDrawOutboxPort}（由该开关守卫）将优先生效；
 * 该远程实现为文档化的扩展点。</p>
 *
 * <p>注意：{@code IDrawOutboxPort} 尚未注入 {@code RaffleApplicationService}，
 * 本类作为默认实现存在。将其接入抽奖热路径需先应用 Outbox DDL 并获得路由审批。</p>
 */
@Slf4j
@Component
@ConditionalOnMissingBean(IDrawOutboxPort.class)
public class LocalDrawOutboxPort implements IDrawOutboxPort {

    @Override
    public void publishDrawSagaStep(DrawOutboxEvent event) {
        log.debug("[LocalDrawOutboxPort] saga step={} userId={} activityId={} orderId={} awardId={}",
                event.getSagaStep(), event.getUserId(), event.getActivityId(),
                event.getOrderId(), event.getAwardId());
    }
}

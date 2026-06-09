package com.dyx.market.infrastructure.adapter.port;

import com.dyx.market.domain.activity.adapter.port.IActivityAccountPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * Local (in-process) implementation of IActivityAccountPort.
 *
 * Phase 2.2-B11 scaffold.
 *
 * This implementation is active when account.service.remote-quota-decrement.enabled=false
 * (the default). It is a deliberate no-op because RaffleActivityPartakeService still
 * calls IActivityRepository.saveCreatePartakeOrderAggregate directly — the quota
 * decrement already happens inside that transaction.
 *
 * The port exists as the routing seam for B12+:
 *   - B12 wires RaffleActivityPartakeService to call decrementQuota via this port.
 *   - AccountRemoteActivityAccountPort (market-service) overrides this bean when
 *     account.service.remote-quota-decrement.enabled=true.
 *
 * Until RaffleActivityPartakeService is rewired (B12+), calling this implementation
 * has no effect on quota state — the real decrement continues to happen inside
 * saveCreatePartakeOrderAggregate.
 */
@Slf4j
@Component
@ConditionalOnMissingBean(IActivityAccountPort.class)
public class LocalActivityAccountPort implements IActivityAccountPort {

    @Override
    public boolean decrementQuota(String userId, Long activityId, String outBusinessNo) {
        // No-op: quota decrement still handled by IActivityRepository.saveCreatePartakeOrderAggregate.
        // This method becomes meaningful in B12 when RaffleActivityPartakeService routes here.
        log.debug("[LocalActivityAccountPort] decrementQuota no-op userId:{} activityId:{} outBusinessNo:{}",
                userId, activityId, outBusinessNo);
        return true;
    }

    @Override
    public void rollbackQuota(String userId, Long activityId, String outBusinessNo) {
        // No-op: local path has no separate decrement to roll back.
        log.debug("[LocalActivityAccountPort] rollbackQuota no-op userId:{} activityId:{} outBusinessNo:{}",
                userId, activityId, outBusinessNo);
    }

}

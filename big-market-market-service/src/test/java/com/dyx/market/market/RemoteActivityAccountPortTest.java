package com.dyx.market.market;

import com.dyx.market.domain.activity.adapter.repository.IActivityRepository;
import com.dyx.market.domain.activity.adapter.port.IPendingRemoteWritePort;
import com.dyx.market.trigger.account.RemoteActivityAccountPort;
import com.dyx.market.trigger.api.IAccountQuotaService;
import com.dyx.market.trigger.api.dto.AccountQuotaDecrementRequestDTO;
import com.dyx.market.types.common.RemoteWriteOperations;
import com.dyx.market.types.exception.AppException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

/** Ensures an UNKNOWN remote rollback is durable rather than silently logged. */
@RunWith(MockitoJUnitRunner.class)
public class RemoteActivityAccountPortTest {

    @Mock
    private IActivityRepository activityRepository;
    @Mock
    private IAccountQuotaService accountQuotaService;
    @Mock
    private IPendingRemoteWritePort pendingRemoteWritePort;

    @InjectMocks
    private RemoteActivityAccountPort remoteActivityAccountPort;

    @Test
    public void rollbackQuota_unknownPersistsReconcileTaskWithOriginalBusinessKey() {
        when(pendingRemoteWritePort.enqueue(eq("draw-1"), eq(RemoteWriteOperations.QUOTA_ROLLBACK),
                any(), eq("user-1"))).thenReturn(true);

        try {
            remoteActivityAccountPort.rollbackQuota("user-1", 100401L, "draw-1");
        } catch (AppException expected) {
            assertEquals("0001", expected.getCode());
            verify(pendingRemoteWritePort).enqueue(eq("draw-1"), eq(RemoteWriteOperations.QUOTA_ROLLBACK),
                    any(), eq("user-1"));
            return;
        }
        throw new AssertionError("UNKNOWN rollback must be reported for reconciliation");
    }

    @Test
    public void decrementQuota_timeoutPersistsRollbackReconcileTaskWithOriginalBusinessKey() {
        when(accountQuotaService.decrementQuota(any(AccountQuotaDecrementRequestDTO.class)))
                .thenThrow(new RuntimeException("timeout"));
        when(pendingRemoteWritePort.enqueue(eq("draw-timeout"), eq(RemoteWriteOperations.QUOTA_ROLLBACK),
                any(), eq("user-1"))).thenReturn(true);

        try {
            remoteActivityAccountPort.decrementQuota("user-1", 100401L, "draw-timeout");
        } catch (AppException expected) {
            assertEquals("0001", expected.getCode());
            verify(pendingRemoteWritePort).enqueue(eq("draw-timeout"), eq(RemoteWriteOperations.QUOTA_ROLLBACK),
                    any(), eq("user-1"));
            return;
        }
        throw new AssertionError("UNKNOWN decrement must be compensated by a durable rollback task");
    }

    @Test
    public void decrementQuota_unknownResponsePersistsRollbackReconcileTaskWithoutReissuingDebit() {
        when(accountQuotaService.decrementQuota(any(AccountQuotaDecrementRequestDTO.class)))
                .thenReturn(com.dyx.market.trigger.api.response.Response.<Boolean>builder()
                        .code("9999").data(null).build());
        when(pendingRemoteWritePort.enqueue(eq("draw-unknown"), eq(RemoteWriteOperations.QUOTA_ROLLBACK),
                any(), eq("user-1"))).thenReturn(true);

        try {
            remoteActivityAccountPort.decrementQuota("user-1", 100401L, "draw-unknown");
        } catch (AppException expected) {
            assertEquals("0001", expected.getCode());
            verify(pendingRemoteWritePort).enqueue(eq("draw-unknown"),
                    eq(RemoteWriteOperations.QUOTA_ROLLBACK), any(), eq("user-1"));
            verify(accountQuotaService, times(1)).decrementQuota(any(AccountQuotaDecrementRequestDTO.class));
            return;
        }
        throw new AssertionError("UNKNOWN decrement response must be handed off for rollback reconciliation");
    }
}

package com.dyx.market.trigger.rpc;

import com.dyx.market.domain.auth.service.AdminAccessService;
import com.dyx.market.trigger.api.IErpOperateService;
import com.dyx.market.trigger.api.dto.ESUserRaffleOrderResponseDTO;
import com.dyx.market.trigger.api.dto.RaffleActivityStageResponseDTO;
import com.dyx.market.trigger.api.dto.UpdateStageActivity2ActiveRequestDTO;
import com.dyx.market.trigger.api.response.Response;
import com.dyx.market.trigger.application.ErpOperateApplicationService;
import com.dyx.market.trigger.http.TriggerApiResponses;
import com.dyx.market.trigger.support.DubboRpcAuthSupport;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import jakarta.annotation.Resource;
import java.util.List;

/**
 * {@link IErpOperateService} 的 Dubbo Provider 实现：ERP 运营查询与上架活动管理。
 *
 * <p>委托 {@link ErpOperateApplicationService} 编排应用服务，响应统一封装为 {@link Response}。</p>
 */
@DubboService(version = "1.0")
@ConditionalOnProperty(name = "erp.embedded-rpc-provider.enabled", havingValue = "true", matchIfMissing = true)
public class ErpOperateServiceRPC implements IErpOperateService {

    @Resource
    private ErpOperateApplicationService erpOperateApplicationService;
    @Resource
    private AdminAccessService adminAccessService;

    @Override
    public Response<List<ESUserRaffleOrderResponseDTO>> queryUserRaffleOrder() {
        DubboRpcAuthSupport.rejectInternalRpc("queryUserRaffleOrder");
        return null;
    }

    @Override
    public Response<List<ESUserRaffleOrderResponseDTO>> queryUserRaffleOrder(String token) {
        DubboRpcAuthSupport.requireAdmin(adminAccessService, token);
        return TriggerApiResponses.ok(erpOperateApplicationService.queryUserRaffleOrderList());
    }

    @Override
    public Response<Boolean> updateStageActivity2Active(UpdateStageActivity2ActiveRequestDTO requestDTO) {
        DubboRpcAuthSupport.rejectInternalRpc("updateStageActivity2Active");
        return null;
    }

    @Override
    public Response<Boolean> updateStageActivity2Active(UpdateStageActivity2ActiveRequestDTO requestDTO, String token) {
        DubboRpcAuthSupport.requireAdmin(adminAccessService, token);
        return TriggerApiResponses.ok(erpOperateApplicationService.updateStageActivity2Active(requestDTO));
    }

    @Override
    public Response<Boolean> updateStageActivity2Expire(UpdateStageActivity2ActiveRequestDTO requestDTO) {
        DubboRpcAuthSupport.rejectInternalRpc("updateStageActivity2Expire");
        return null;
    }

    @Override
    public Response<Boolean> updateStageActivity2Expire(UpdateStageActivity2ActiveRequestDTO requestDTO, String token) {
        DubboRpcAuthSupport.requireAdmin(adminAccessService, token);
        return TriggerApiResponses.ok(erpOperateApplicationService.updateStageActivity2Expire(requestDTO));
    }

    @Override
    public Response<List<RaffleActivityStageResponseDTO>> queryRaffleActivityStageList() {
        DubboRpcAuthSupport.rejectInternalRpc("queryRaffleActivityStageList");
        return null;
    }

    @Override
    public Response<List<RaffleActivityStageResponseDTO>> queryRaffleActivityStageList(String token) {
        DubboRpcAuthSupport.requireAdmin(adminAccessService, token);
        return TriggerApiResponses.ok(erpOperateApplicationService.queryStageList());
    }
}

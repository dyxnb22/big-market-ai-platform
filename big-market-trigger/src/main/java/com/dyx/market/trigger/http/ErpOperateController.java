package com.dyx.market.trigger.http;

import com.dyx.market.trigger.api.IErpOperateService;
import com.dyx.market.trigger.api.dto.ESUserRaffleOrderResponseDTO;
import com.dyx.market.trigger.api.dto.RaffleActivityStageResponseDTO;
import com.dyx.market.trigger.api.dto.UpdateStageActivity2ActiveRequestDTO;
import com.dyx.market.trigger.api.response.Response;
import com.dyx.market.trigger.application.ErpOperateApplicationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

/**
 * ERP 运营 HTTP 入口：抽奖单查询、上架活动生效与阶段列表等运营接口。
 */
@Slf4j
@RestController()
@CrossOrigin("${app.config.cross-origin}")
@RequestMapping("/api/${app.config.api-version}/raffle/erp/")
public class ErpOperateController implements IErpOperateService {

    @Resource
    private ErpOperateApplicationService erpOperateApplicationService;

    @Override
    public Response<List<ESUserRaffleOrderResponseDTO>> queryUserRaffleOrder() {
        return TriggerApiResponses.ok(erpOperateApplicationService.queryUserRaffleOrderList());
    }

    @GetMapping("query_user_raffle_order")
    @Override
    public Response<List<ESUserRaffleOrderResponseDTO>> queryUserRaffleOrder(
            @RequestHeader(value = "X-Admin-Token", required = false) String token) {
        return queryUserRaffleOrder();
    }

    @Override
    public Response<Boolean> updateStageActivity2Active(UpdateStageActivity2ActiveRequestDTO requestDTO) {
        return TriggerApiResponses.ok(erpOperateApplicationService.updateStageActivity2Active(requestDTO));
    }

    @PostMapping("update_stage_activity_2_active")
    @Override
    public Response<Boolean> updateStageActivity2Active(@RequestBody UpdateStageActivity2ActiveRequestDTO requestDTO,
                                                        @RequestHeader(value = "X-Admin-Token", required = false) String token) {
        return updateStageActivity2Active(requestDTO);
    }

    @Override
    public Response<Boolean> updateStageActivity2Expire(UpdateStageActivity2ActiveRequestDTO requestDTO) {
        return TriggerApiResponses.ok(erpOperateApplicationService.updateStageActivity2Expire(requestDTO));
    }

    @PostMapping("update_stage_activity_2_expire")
    @Override
    public Response<Boolean> updateStageActivity2Expire(@RequestBody UpdateStageActivity2ActiveRequestDTO requestDTO,
                                                          @RequestHeader(value = "X-Admin-Token", required = false) String token) {
        return updateStageActivity2Expire(requestDTO);
    }

    @Override
    public Response<List<RaffleActivityStageResponseDTO>> queryRaffleActivityStageList() {
        return TriggerApiResponses.ok(erpOperateApplicationService.queryStageList());
    }

    @GetMapping("query_raffle_activity_stage_list")
    @Override
    public Response<List<RaffleActivityStageResponseDTO>> queryRaffleActivityStageList(
            @RequestHeader(value = "X-Admin-Token", required = false) String token) {
        return queryRaffleActivityStageList();
    }
}

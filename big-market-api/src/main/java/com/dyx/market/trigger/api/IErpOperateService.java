package com.dyx.market.trigger.api;

import com.dyx.market.trigger.api.dto.ESUserRaffleOrderResponseDTO;
import com.dyx.market.trigger.api.dto.RaffleActivityStageResponseDTO;
import com.dyx.market.trigger.api.dto.UpdateStageActivity2ActiveRequestDTO;
import com.dyx.market.trigger.api.response.Response;

import java.util.List;

/**
 * @author Fuzhengwei bugstack.cn @小傅哥
 * @description ERP 运营接口
 * @create 2024-09-21 12:26
 */
public interface IErpOperateService {

    Response<List<ESUserRaffleOrderResponseDTO>> queryUserRaffleOrder();

    Response<List<ESUserRaffleOrderResponseDTO>> queryUserRaffleOrder(String token);

    /**
     * 上架活动，上架后驱动装配
     *
     * @param requestDTO 上架流水ID
     */
    Response<Boolean> updateStageActivity2Active(UpdateStageActivity2ActiveRequestDTO requestDTO);

    /**
     * 上架活动，上架后驱动装配（带管理员token）
     */
    Response<Boolean> updateStageActivity2Active(UpdateStageActivity2ActiveRequestDTO requestDTO, String token);

    /**
     * 下架活动：stage → expire，raffle_activity.state → close
     */
    Response<Boolean> updateStageActivity2Expire(UpdateStageActivity2ActiveRequestDTO requestDTO);

    Response<Boolean> updateStageActivity2Expire(UpdateStageActivity2ActiveRequestDTO requestDTO, String token);

    Response<List<RaffleActivityStageResponseDTO>> queryRaffleActivityStageList();

    Response<List<RaffleActivityStageResponseDTO>> queryRaffleActivityStageList(String token);

}

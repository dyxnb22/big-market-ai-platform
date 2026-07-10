package com.dyx.market.trigger.application;

import com.alibaba.fastjson.JSON;
import com.dyx.market.domain.activity.adapter.repository.IActivityRepository;
import com.dyx.market.domain.activity.model.entity.RaffleActivityStageEntity;
import com.dyx.market.domain.activity.model.valobj.ActivityStateVO;
import com.dyx.market.domain.activity.service.IRaffleActivityStageService;
import com.dyx.market.domain.activity.service.armory.IActivityArmory;
import com.dyx.market.domain.strategy.service.armory.IStrategyArmory;
import com.dyx.market.queries.adapter.repository.IESUserRaffleOrderRepository;
import com.dyx.market.queries.model.valobj.ESUserRaffleOrderVO;
import com.dyx.market.trigger.api.dto.ESUserRaffleOrderResponseDTO;
import com.dyx.market.trigger.api.dto.RaffleActivityStageResponseDTO;
import com.dyx.market.trigger.api.dto.UpdateStageActivity2ActiveRequestDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

/**
 * ERP 运营应用服务：查询 ES 抽奖单、上架活动生效与阶段列表。
 */
@Slf4j
@Service
public class ErpOperateApplicationService {

    private static final int MAX_RESULT_LIMIT = 100;

    @Resource
    private IESUserRaffleOrderRepository userRaffleOrderRepository;
    @Resource
    private IRaffleActivityStageService raffleActivityStageService;
    @Resource
    private IActivityArmory activityArmory;
    @Resource
    private IStrategyArmory strategyArmory;
    @Resource
    private IActivityRepository activityRepository;

    public List<ESUserRaffleOrderResponseDTO> queryUserRaffleOrderList() {
        log.info("查询运营数据，用户抽奖单列表");
        List<ESUserRaffleOrderVO> userRaffleOrderVOList = userRaffleOrderRepository.queryESUserRaffleOrderVOList();
        List<ESUserRaffleOrderResponseDTO> result = new ArrayList<>();
        for (ESUserRaffleOrderVO vo : userRaffleOrderVOList) {
            ESUserRaffleOrderResponseDTO dto = new ESUserRaffleOrderResponseDTO();
            dto.setUserId(vo.getUserId());
            dto.setActivityId(vo.getActivityId());
            dto.setActivityName(vo.getActivityName());
            dto.setStrategyId(vo.getStrategyId());
            dto.setOrderId(vo.getOrderId());
            dto.setOrderTime(vo.getOrderTime());
            dto.setOrderState(vo.getOrderState());
            dto.setCreateTime(vo.getCreateTime());
            dto.setUpdateTime(vo.getUpdateTime());
            result.add(dto);
            if (result.size() >= MAX_RESULT_LIMIT) {
                log.info("ERP查询结果超出限制，截取前{}条", MAX_RESULT_LIMIT);
                break;
            }
        }
        return result;
    }

    public boolean updateStageActivity2Active(UpdateStageActivity2ActiveRequestDTO requestDTO) {
        Long id = requestDTO.getId();
        log.info("更新上架活动状态为生效开始 id:{}", id);
        Long activityId = raffleActivityStageService.queryStageActivity2ActiveById(id);
        boolean assembled = activityArmory.assembleActivitySkuByActivityId(activityId);
        strategyArmory.assembleLotteryStrategyByActivityId(activityId);
        log.info("更新上架活动状态为装配完成 activityId:{} assembled:{}", activityId, assembled);
        raffleActivityStageService.updateStageActivity2Active(id);
        activityRepository.updateRaffleActivityState(activityId, ActivityStateVO.open.getCode());
        log.info("更新上架活动状态为生效完成 activityId:{}", activityId);
        return true;
    }

    public boolean updateStageActivity2Expire(UpdateStageActivity2ActiveRequestDTO requestDTO) {
        Long id = requestDTO.getId();
        log.info("更新上架活动状态为下架开始 id:{}", id);
        Long activityId = raffleActivityStageService.queryStageActivity2ActiveById(id);
        raffleActivityStageService.updateStageActivity2Expire(id);
        activityRepository.updateRaffleActivityState(activityId, ActivityStateVO.close.getCode());
        log.info("更新上架活动状态为下架完成 activityId:{}", activityId);
        return true;
    }

    public List<RaffleActivityStageResponseDTO> queryStageList() {
        List<RaffleActivityStageResponseDTO> result = new ArrayList<>();
        List<RaffleActivityStageEntity> entities = raffleActivityStageService.queryStageActivityList();
        for (RaffleActivityStageEntity stage : entities) {
            result.add(RaffleActivityStageResponseDTO.builder()
                    .id(stage.getId())
                    .channel(stage.getChannel())
                    .source(stage.getSource())
                    .activityId(stage.getActivityId())
                    .state(stage.getState())
                    .build());
        }
        log.info("查询上架活动数据 {}", JSON.toJSONString(result));
        return result;
    }
}

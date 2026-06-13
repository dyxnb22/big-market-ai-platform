package com.dyx.market.trigger.http;

import com.dyx.market.domain.activity.model.entity.RaffleActivityStageEntity;
import com.dyx.market.domain.activity.service.IRaffleActivityStageService;
import com.dyx.market.domain.activity.service.armory.IActivityArmory;
import com.dyx.market.queries.adapter.repository.IESUserRaffleOrderRepository;
import com.dyx.market.queries.model.valobj.ESUserRaffleOrderVO;
import com.dyx.market.trigger.api.IErpOperateService;
import com.dyx.market.trigger.api.dto.ESUserRaffleOrderResponseDTO;
import com.dyx.market.trigger.api.dto.RaffleActivityStageResponseDTO;
import com.dyx.market.trigger.api.dto.UpdateStageActivity2ActiveRequestDTO;
import com.dyx.market.trigger.api.response.Response;
import com.dyx.market.types.enums.ResponseCode;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Fuzhengwei bugstack.cn @小傅哥
 * @description ERP 运营接口
 * @create 2024-09-21 12:25
 */
@Slf4j
@RestController()
@CrossOrigin("${app.config.cross-origin}")
@RequestMapping("/api/${app.config.api-version}/raffle/erp/")
@DubboService(version = "1.0")
public class ErpOperateController implements IErpOperateService {

    @Value("${erp.admin.token:${app.admin.token:admin-dev-token}}")
    private String adminToken;

    private static final int MAX_RESULT_LIMIT = 100;

    @Resource
    private IESUserRaffleOrderRepository userRaffleOrderRepository;
    @Resource
    private IRaffleActivityStageService raffleActivityStageService;
    @Resource
    private IActivityArmory activityArmory;

    @Override
    public Response<List<ESUserRaffleOrderResponseDTO>> queryUserRaffleOrder() {
        return queryUserRaffleOrder(null);
    }

    /**
     * 查询运营数据，用户抽奖单列表
     * curl --request GET --url 'http://localhost:8080/api/v1/raffle/erp/query_user_raffle_order'
     */
    @RequestMapping(value = "query_user_raffle_order", method = RequestMethod.GET)
    @Override
    public Response<List<ESUserRaffleOrderResponseDTO>> queryUserRaffleOrder(
            @RequestHeader(value = "X-Admin-Token", required = false) String token) {
        try {
            if (!hasAdminAccess(token)) {
                log.warn("ERP 查询非法token");
                return Response.<List<ESUserRaffleOrderResponseDTO>>builder()
                        .code(ResponseCode.APP_TOKEN_ERROR.getCode())
                        .info(ResponseCode.APP_TOKEN_ERROR.getInfo())
                        .build();
            }
            log.info("查询运营数据，用户抽奖单列表");
            List<ESUserRaffleOrderVO> userRaffleOrderVOList = userRaffleOrderRepository.queryESUserRaffleOrderVOList();

            List<ESUserRaffleOrderResponseDTO> userRaffleOrderResponseDTOS = new ArrayList<>();
            for (ESUserRaffleOrderVO esUserRaffleOrderVO : userRaffleOrderVOList) {
                ESUserRaffleOrderResponseDTO esUserRaffleOrderResponseDTO = new ESUserRaffleOrderResponseDTO();
                esUserRaffleOrderResponseDTO.setUserId(esUserRaffleOrderVO.getUserId());
                esUserRaffleOrderResponseDTO.setActivityId(esUserRaffleOrderVO.getActivityId());
                esUserRaffleOrderResponseDTO.setActivityName(esUserRaffleOrderVO.getActivityName());
                esUserRaffleOrderResponseDTO.setStrategyId(esUserRaffleOrderVO.getStrategyId());
                esUserRaffleOrderResponseDTO.setOrderId(esUserRaffleOrderVO.getOrderId());
                esUserRaffleOrderResponseDTO.setOrderTime(esUserRaffleOrderVO.getOrderTime());
                esUserRaffleOrderResponseDTO.setOrderState(esUserRaffleOrderVO.getOrderState());
                esUserRaffleOrderResponseDTO.setCreateTime(esUserRaffleOrderVO.getCreateTime());
                esUserRaffleOrderResponseDTO.setUpdateTime(esUserRaffleOrderVO.getUpdateTime());
                userRaffleOrderResponseDTOS.add(esUserRaffleOrderResponseDTO);
                if (userRaffleOrderResponseDTOS.size() >= MAX_RESULT_LIMIT) {
                    log.info("ERP查询结果超出限制，截取前{}条", MAX_RESULT_LIMIT);
                    break;
                }
            }

            return Response.<List<ESUserRaffleOrderResponseDTO>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(userRaffleOrderResponseDTOS)
                    .build();
        } catch (Exception e) {
            log.error("查询运营数据，用户抽奖单列表", e);
            return Response.<List<ESUserRaffleOrderResponseDTO>>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

    @Override
    public Response<Boolean> updateStageActivity2Active(UpdateStageActivity2ActiveRequestDTO requestDTO) {
        return updateStageActivity2Active(requestDTO, null);
    }

    @RequestMapping(value = "update_stage_activity_2_active", method = RequestMethod.POST)
    @Override
    public Response<Boolean> updateStageActivity2Active(@RequestBody UpdateStageActivity2ActiveRequestDTO requestDTO,
                                                        @RequestHeader(value = "X-Admin-Token", required = false) String token) {
        try {
            if (!hasAdminAccess(token)) {
                log.warn("ERP 操作非法token");
                return Response.<Boolean>builder()
                        .code(ResponseCode.APP_TOKEN_ERROR.getCode())
                        .info(ResponseCode.APP_TOKEN_ERROR.getInfo())
                        .build();
            }
            Long id = requestDTO.getId();
            log.info("更新上架活动状态为生效开始 id:{}", id);
            Long activityId = raffleActivityStageService.queryStageActivity2ActiveById(id);
            boolean assembled = activityArmory.assembleActivitySkuByActivityId(activityId);
            log.info("更新上架活动状态为装配完成 activityId:{} assembled:{}", activityId, assembled);

            raffleActivityStageService.updateStageActivity2Active(id);
            log.info("更新上架活动状态为生效完成 activityId:{}", activityId);

            return Response.<Boolean>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(true)
                    .build();
        } catch (Exception e) {
            log.error("更新上架活动状态为生效开始，失败 id:{}", requestDTO.getId(), e);
            return Response.<Boolean>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

    @Override
    public Response<List<RaffleActivityStageResponseDTO>> queryRaffleActivityStageList() {
        return queryRaffleActivityStageList(null);
    }

    @RequestMapping(value = "query_raffle_activity_stage_list", method = RequestMethod.GET)
    @Override
    public Response<List<RaffleActivityStageResponseDTO>> queryRaffleActivityStageList(
            @RequestHeader(value = "X-Admin-Token", required = false) String token) {
        try {
            if (!hasAdminAccess(token)) {
                log.warn("ERP 查询非法token");
                return Response.<List<RaffleActivityStageResponseDTO>>builder()
                        .code(ResponseCode.APP_TOKEN_ERROR.getCode())
                        .info(ResponseCode.APP_TOKEN_ERROR.getInfo())
                        .build();
            }
            List<RaffleActivityStageResponseDTO> raffleActivityStageResponseDTOS = new ArrayList<>();
            List<RaffleActivityStageEntity> raffleActivityStageEntities = raffleActivityStageService.queryStageActivityList();
            for (RaffleActivityStageEntity raffleActivityStage : raffleActivityStageEntities) {
                RaffleActivityStageResponseDTO raffleActivityStageResponseDTO = RaffleActivityStageResponseDTO.builder()
                        .id(raffleActivityStage.getId())
                        .channel(raffleActivityStage.getChannel())
                        .source(raffleActivityStage.getSource())
                        .activityId(raffleActivityStage.getActivityId())
                        .state(raffleActivityStage.getState())
                        .build();
                raffleActivityStageResponseDTOS.add(raffleActivityStageResponseDTO);
            }

            Response<List<RaffleActivityStageResponseDTO>> response = Response.<List<RaffleActivityStageResponseDTO>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(raffleActivityStageResponseDTOS)
                    .build();

            log.info("查询上架活动数据 {}", JSON.toJSONString(response));

            return response;
        } catch (Exception e) {
            log.error("查询上架活动数据失败", e);
            return Response.<List<RaffleActivityStageResponseDTO>>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

    private boolean hasAdminAccess(String token) {
        if (adminToken.equals(token)) {
            return true;
        }
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        return attributes instanceof ServletRequestAttributes
                && ((ServletRequestAttributes) attributes).getRequest().getAttribute("userId") != null;
    }

}

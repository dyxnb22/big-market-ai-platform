package com.dyx.market.trigger.application;

import com.alibaba.fastjson.JSON;
import com.dyx.market.domain.activity.model.entity.ActivityAccountEntity;
import com.dyx.market.domain.activity.model.entity.SkuProductEntity;
import com.dyx.market.domain.activity.service.IRaffleActivitySkuProductService;
import com.dyx.market.trigger.adapter.IAccountReadAdapter;
import com.dyx.market.trigger.api.dto.SkuProductResponseDTO;
import com.dyx.market.trigger.api.dto.UserActivityAccountRequestDTO;
import com.dyx.market.trigger.api.dto.UserActivityAccountResponseDTO;
import com.dyx.market.types.enums.ResponseCode;
import com.dyx.market.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 活动账户、积分、SKU 商品查询应用服务。
 */
@Slf4j
@Service
public class RaffleActivityQueryApplicationService {

    @Resource
    private IAccountReadAdapter accountRemoteReadAdapter;
    @Resource
    private IRaffleActivitySkuProductService raffleActivitySkuProductService;

    public UserActivityAccountResponseDTO queryUserActivityAccount(UserActivityAccountRequestDTO request) {
        log.info("查询用户活动账户开始 userId:{} activityId:{}", request.getUserId(), request.getActivityId());
        if (StringUtils.isBlank(request.getUserId()) || null == request.getActivityId()) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), ResponseCode.ILLEGAL_PARAMETER.getInfo());
        }
        ActivityAccountEntity activityAccountEntity = accountRemoteReadAdapter
                .queryActivityAccountEntity(request.getActivityId(), request.getUserId());
        UserActivityAccountResponseDTO dto = UserActivityAccountResponseDTO.builder()
                .totalCount(activityAccountEntity.getTotalCount())
                .totalCountSurplus(activityAccountEntity.getTotalCountSurplus())
                .dayCount(activityAccountEntity.getDayCount())
                .dayCountSurplus(activityAccountEntity.getDayCountSurplus())
                .monthCount(activityAccountEntity.getMonthCount())
                .monthCountSurplus(activityAccountEntity.getMonthCountSurplus())
                .build();
        log.info("查询用户活动账户完成 userId:{} activityId:{} dto:{}",
                request.getUserId(), request.getActivityId(), JSON.toJSONString(dto));
        return dto;
    }

    public BigDecimal queryUserCreditAccount(String userId) {
        log.info("查询用户积分值开始 userId:{}", userId);
        if (StringUtils.isBlank(userId)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), ResponseCode.ILLEGAL_PARAMETER.getInfo());
        }
        BigDecimal balance = accountRemoteReadAdapter.queryUserCreditAccount(userId);
        log.info("查询用户积分值完成 userId:{} adjustAmount:{}", userId, balance);
        return balance;
    }

    public List<SkuProductResponseDTO> querySkuProductListByActivityId(Long activityId) {
        log.info("查询sku商品集合开始 activityId:{}", activityId);
        if (null == activityId) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), ResponseCode.ILLEGAL_PARAMETER.getInfo());
        }
        List<SkuProductEntity> skuProductEntities = raffleActivitySkuProductService
                .querySkuProductEntityListByActivityId(activityId);
        List<SkuProductResponseDTO> result = new ArrayList<>(skuProductEntities.size());
        for (SkuProductEntity entity : skuProductEntities) {
            result.add(toSkuProductResponse(entity));
        }
        log.info("查询sku商品集合完成 activityId:{} size:{}", activityId, result.size());
        return result;
    }

    private static SkuProductResponseDTO toSkuProductResponse(SkuProductEntity entity) {
        SkuProductResponseDTO.ActivityCount activityCount = new SkuProductResponseDTO.ActivityCount();
        activityCount.setTotalCount(entity.getActivityCount().getTotalCount());
        activityCount.setMonthCount(entity.getActivityCount().getMonthCount());
        activityCount.setDayCount(entity.getActivityCount().getDayCount());

        SkuProductResponseDTO dto = new SkuProductResponseDTO();
        dto.setSku(entity.getSku());
        dto.setActivityId(entity.getActivityId());
        dto.setActivityCountId(entity.getActivityCountId());
        dto.setStockCount(entity.getStockCount());
        dto.setStockCountSurplus(entity.getStockCountSurplus());
        dto.setProductAmount(entity.getProductAmount());
        dto.setActivityCount(activityCount);
        return dto;
    }
}

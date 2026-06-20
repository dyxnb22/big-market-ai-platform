package com.dyx.market.infrastructure.adapter.port;

import com.dyx.market.domain.award.adapter.port.IAwardPort;
import com.dyx.market.infrastructure.gateway.IOpenAIAccountService;
import com.dyx.market.infrastructure.gateway.dto.AdjustQuotaRequestDTO;
import com.dyx.market.infrastructure.gateway.dto.AdjustQuotaResponseDTO;
import com.dyx.market.types.common.Response;
import com.dyx.market.types.enums.ResponseCode;
import com.dyx.market.types.exception.AppException;
import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import retrofit2.Call;

import javax.annotation.Resource;

/**
 * @author Fuzhengwei bugstack.cn @小傅哥
 * @description
 * @create 2024-10-06 11:46
 */
@Slf4j
@Service
public class AwardPort implements IAwardPort {

    @Value("${gateway.config.big-market-appId}")
    private String bigMarketAppId;
    @Value("${gateway.config.big-market-appToken}")
    private String bigMarketAppToken;

    @Resource
    private IOpenAIAccountService openAIAccountService;

    @Override
    public void adjustAmount(String userId, Integer increaseQuota) throws Exception {
        try {
            AdjustQuotaRequestDTO requestDTO = AdjustQuotaRequestDTO.builder()
                    .appId(bigMarketAppId)
                    .appToken(bigMarketAppToken)
                    .openid(userId)
                    .increaseQuota(increaseQuota)
                    .build();

            Call<Response<AdjustQuotaResponseDTO>> call = openAIAccountService.adjustQuota(requestDTO);
            Response<AdjustQuotaResponseDTO> response = call.execute().body();
            log.info("请求OpenAI应用账户调额接口完成 userId:{} increaseQuota:{} response:{}", userId, increaseQuota, JSON.toJSONString(response));

            if (response == null || !ResponseCode.SUCCESS.getCode().equals(response.getCode())) {
                throw new AppException(ResponseCode.GATEWAY_ERROR.getCode(), ResponseCode.GATEWAY_ERROR.getInfo());
            }

        } catch (Exception e) {
            log.error("请求OpenAI应用账户调额接口失败 userId:{} increaseQuota:{}", userId, increaseQuota, e);
            throw e;
        }

    }

}

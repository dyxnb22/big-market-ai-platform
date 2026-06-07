package com.dyx.market.trigger.api;

import com.dyx.market.trigger.api.dto.RebateRequestDTO;
import com.dyx.market.trigger.api.request.Request;
import com.dyx.market.trigger.api.response.Response;

/**
 * @author Fuzhengwei bugstack.cn @小傅哥
 * @description 返利服务
 * @create 2024-10-20 13:38
 */
public interface IRebateService {

    Response<Boolean> rebate(Request<RebateRequestDTO> request);

}

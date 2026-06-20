package com.dyx.market.domain.rebate.support;

import com.dyx.market.trigger.api.request.Request;
import com.dyx.market.types.enums.ResponseCode;
import com.dyx.market.types.exception.AppException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Map;

@Component
public class RebateAppTokenValidator {

    @Resource
    private Map<String, String> appTokenMap;

    public void validate(Request<?> request) {
        if (request == null
                || StringUtils.isBlank(request.getAppId())
                || StringUtils.isBlank(request.getAppToken())) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), ResponseCode.ILLEGAL_PARAMETER.getInfo());
        }
        if (!request.getAppToken().equals(appTokenMap.get(request.getAppId()))) {
            throw new AppException(ResponseCode.APP_TOKEN_ERROR.getCode(), ResponseCode.APP_TOKEN_ERROR.getInfo());
        }
    }

    public <T> Request<T> buildRequest(String appId, T data) {
        if (StringUtils.isBlank(appId)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), ResponseCode.ILLEGAL_PARAMETER.getInfo());
        }
        String appToken = appTokenMap.get(appId);
        if (StringUtils.isBlank(appToken)) {
            throw new AppException(ResponseCode.APP_TOKEN_ERROR.getCode(), ResponseCode.APP_TOKEN_ERROR.getInfo());
        }
        return Request.<T>builder().appId(appId).appToken(appToken).data(data).build();
    }
}

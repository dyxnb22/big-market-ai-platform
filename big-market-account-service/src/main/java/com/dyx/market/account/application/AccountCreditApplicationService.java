package com.dyx.market.account.application;

import com.dyx.market.domain.credit.model.entity.CreditAccountEntity;
import com.dyx.market.domain.credit.model.entity.TradeEntity;
import com.dyx.market.domain.credit.model.valobj.TradeNameVO;
import com.dyx.market.domain.credit.model.valobj.TradeTypeVO;
import com.dyx.market.domain.credit.service.ICreditAdjustService;
import com.dyx.market.infrastructure.dao.IUserCreditOrderDao;
import com.dyx.market.infrastructure.dao.po.UserCreditOrder;
import com.dyx.market.middleware.db.router.strategy.IDBRouterStrategy;
import com.dyx.market.trigger.api.dto.CreditTradeRequestDTO;
import com.dyx.market.types.enums.ResponseCode;
import com.dyx.market.types.exception.AppException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.math.BigDecimal;

/**
 * 积分账户应用服务：创建积分交易订单、查询可用余额。
 *
 * <p>适配 RPC 入参，委托 {@link ICreditAdjustService} 完成领域逻辑。</p>
 */
@Service
public class AccountCreditApplicationService {

    @Resource
    private ICreditAdjustService creditAdjustService;
    @Resource
    private IUserCreditOrderDao userCreditOrderDao;
    @Resource
    private IDBRouterStrategy dbRouter;

    /** 创建积分交易订单（赚取或消费积分）。 */
    public String createOrder(CreditTradeRequestDTO request) {
        if (request == null || StringUtils.isBlank(request.getUserId())
                || StringUtils.isBlank(request.getTradeName())
                || StringUtils.isBlank(request.getTradeType())
                || request.getAmount() == null
                || StringUtils.isBlank(request.getOutBusinessNo())) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), ResponseCode.ILLEGAL_PARAMETER.getInfo());
        }
        return creditAdjustService.createOrder(TradeEntity.builder()
                .userId(request.getUserId())
                .tradeName(resolveTradeNameVO(request.getTradeName()))
                .tradeType(resolveTradeType(request.getTradeType()))
                .amount(request.getAmount())
                .outBusinessNo(request.getOutBusinessNo())
                .build());
    }

    /** 查询用户当前可用积分余额。 */
    public BigDecimal queryUserCreditAccount(String userId) {
        if (StringUtils.isBlank(userId)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), ResponseCode.ILLEGAL_PARAMETER.getInfo());
        }
        CreditAccountEntity entity = creditAdjustService.queryUserCreditAccount(userId);
        return entity != null ? entity.getAdjustAmount() : BigDecimal.ZERO;
    }

    /** 按 outBusinessNo 查询积分流水是否已存在。 */
    public boolean existsCreditOrder(String userId, String outBusinessNo) {
        if (StringUtils.isBlank(userId) || StringUtils.isBlank(outBusinessNo)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), ResponseCode.ILLEGAL_PARAMETER.getInfo());
        }
        try {
            dbRouter.doRouter(userId);
            UserCreditOrder query = new UserCreditOrder();
            query.setUserId(userId);
            query.setOutBusinessNo(outBusinessNo);
            return userCreditOrderDao.queryByOutBusinessNo(query) != null;
        } finally {
            dbRouter.clear();
        }
    }

    private TradeNameVO resolveTradeNameVO(String name) {
        try {
            return TradeNameVO.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "Unknown tradeName: " + name);
        }
    }

    private TradeTypeVO resolveTradeType(String code) {
        for (TradeTypeVO type : TradeTypeVO.values()) {
            if (type.getCode().equalsIgnoreCase(code)) {
                return type;
            }
        }
        throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(),
                "Unknown tradeType: " + code + ". Expected: forward | reverse");
    }
}

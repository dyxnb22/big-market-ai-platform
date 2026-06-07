package com.dyx.market.trigger.http;

import com.dyx.market.domain.activity.application.ActivityDrawRequestEntity;
import com.dyx.market.domain.activity.application.ActivityDrawResponseEntity;
import com.dyx.market.domain.activity.application.RaffleApplicationService;
import com.dyx.market.domain.activity.model.entity.*;
import com.dyx.market.domain.activity.model.valobj.OrderTradeTypeVO;
import com.dyx.market.domain.activity.service.IRaffleActivityAccountQuotaService;
import com.dyx.market.domain.activity.service.IRaffleActivityPartakeService;
import com.dyx.market.domain.activity.service.IRaffleActivitySkuProductService;
import com.dyx.market.domain.activity.service.IRaffleActivityStageService;
import com.dyx.market.domain.activity.service.armory.IActivityArmory;
import com.dyx.market.domain.auth.service.IAuthService;
import com.dyx.market.domain.award.model.entity.UserAwardRecordEntity;
import com.dyx.market.domain.award.model.valobj.AwardStateVO;
import com.dyx.market.domain.award.service.IAwardService;
import com.dyx.market.domain.credit.model.entity.CreditAccountEntity;
import com.dyx.market.domain.credit.model.entity.TradeEntity;
import com.dyx.market.domain.credit.model.valobj.TradeNameVO;
import com.dyx.market.domain.credit.model.valobj.TradeTypeVO;
import com.dyx.market.domain.credit.service.ICreditAdjustService;
import com.dyx.market.domain.rebate.model.entity.BehaviorEntity;
import com.dyx.market.domain.rebate.model.entity.BehaviorRebateOrderEntity;
import com.dyx.market.domain.rebate.model.valobj.BehaviorTypeVO;
import com.dyx.market.domain.rebate.service.IBehaviorRebateService;
import com.dyx.market.domain.strategy.model.entity.RaffleAwardEntity;
import com.dyx.market.domain.strategy.model.entity.RaffleFactorEntity;
import com.dyx.market.domain.strategy.service.IRaffleStrategy;
import com.dyx.market.domain.strategy.service.armory.IStrategyArmory;
import com.dyx.market.trigger.api.IRaffleActivityService;
import com.dyx.market.trigger.api.dto.*;
import com.dyx.market.types.annotations.DCCValue;
import com.dyx.market.types.annotations.RateLimiterAccessInterceptor;
import com.dyx.market.types.enums.ResponseCode;
import com.dyx.market.types.exception.AppException;
import com.dyx.market.trigger.api.response.Response;
import com.alibaba.fastjson.JSON;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixProperty;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * @author Fuzhengwei bugstack.cn @小傅哥
 * @description 抽奖活动服务 注意；在不引用 application/case 层的时候，就需要让接口实现层来做领域的串联。一些较大规模的系统，需要加入 case 层。
 * @create 2024-04-13 09:42
 */
@Slf4j
@RestController()
@CrossOrigin("${app.config.cross-origin}")
@RequestMapping("/api/${app.config.api-version}/raffle/activity/")
@DubboService(version = "1.0")
public class RaffleActivityController implements IRaffleActivityService {

    private static final DateTimeFormatter DATE_FORMAT_DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Resource
    private IRaffleActivityPartakeService raffleActivityPartakeService;
    @Resource
    private IRaffleActivityAccountQuotaService raffleActivityAccountQuotaService;
    @Resource
    private IRaffleActivitySkuProductService raffleActivitySkuProductService;
    @Resource
    private IRaffleStrategy raffleStrategy;
    @Resource
    private IAwardService awardService;
    @Resource
    private IActivityArmory activityArmory;
    @Resource
    private IStrategyArmory strategyArmory;
    @Resource
    private RaffleApplicationService raffleApplicationService;
    @Resource
    private IBehaviorRebateService behaviorRebateService;
    @Resource
    private ICreditAdjustService creditAdjustService;
    @Resource
    private IAuthService authService;
    @Resource
    private IRaffleActivityStageService raffleActivityStageService;
    @Resource
    private HttpServletRequest httpServletRequest;

    // dcc 统一配置中心动态配置降级开关
    @DCCValue("degradeSwitch:close")
    private String degradeSwitch;

    @RequestMapping(value = "query_stage_activity_id", method = RequestMethod.GET)
    @Override
    public Response<Long> queryStageActivityId(@RequestParam String channel, @RequestParam String source) {
        try {
            Long activityId = raffleActivityStageService.queryStageActivityId(channel, source);
            log.info("查询上架活动ID channel:{} source:{} activity:{}", channel, source, activityId);
            return Response.<Long>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(activityId)
                    .build();
        } catch (Exception e) {
            log.info("查询上架活动ID异常 channel:{} source:{}", channel, source, e);
            return Response.<Long>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

    /**
     * 活动装配 - 数据预热 | 把活动配置的对应的 sku 一起装配
     *
     * @param activityId 活动ID
     * @return 装配结果
     * <p>
     * 接口：<a href="http://localhost:8091/api/v1/raffle/activity/armory">/api/v1/raffle/activity/armory</a>
     * 入参：{"activityId":100001,"userId":"xiaofuge"}
     * <p>
     * curl --request GET \
     * --url 'http://localhost:8091/api/v1/raffle/activity/armory?activityId=100301'
     */
    @RequestMapping(value = "armory", method = RequestMethod.GET)
    @Override
    public Response<Boolean> armory(@RequestParam Long activityId) {
        try {
            log.info("活动装配，数据预热，开始 activityId:{}", activityId);
            // 0. 参数校验
            if (null == activityId) {
                throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), ResponseCode.ILLEGAL_PARAMETER.getInfo());
            }
            // 1. 活动装配
            activityArmory.assembleActivitySkuByActivityId(activityId);
            // 2. 策略装配
            strategyArmory.assembleLotteryStrategyByActivityId(activityId);
            Response<Boolean> response = Response.<Boolean>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(true)
                    .build();
            log.info("活动装配，数据预热，完成 activityId:{}", activityId);
            return response;
        } catch (Exception e) {
            log.error("活动装配，数据预热，失败 activityId:{}", activityId, e);
            return Response.<Boolean>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

    @RequestMapping(value = "draw_by_token", method = RequestMethod.POST)
    @Override
    public Response<ActivityDrawResponseDTO> draw(@RequestHeader("Authorization") String token, @RequestBody ActivityDrawRequestDTO request) {
        try {
            String openid = (String) httpServletRequest.getAttribute("userId");
            if (StringUtils.isBlank(openid)) {
                return Response.<ActivityDrawResponseDTO>builder()
                        .code(ResponseCode.Login.TOKEN_ERROR.getCode())
                        .info(ResponseCode.Login.TOKEN_ERROR.getInfo())
                        .build();
            }

            log.info("活动抽奖开始 userId:{}", openid);
            request.setUserId(openid);

            return draw(request);
        } catch (Exception e) {
            log.error("活动抽奖失败 userId:{} activityId:{}", request.getUserId(), request.getActivityId(), e);
            return Response.<ActivityDrawResponseDTO>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

    /**
     * 抽奖接口
     *
     * @param request 请求对象
     * @return 抽奖结果
     * <p>
     * 接口：<a href="http://localhost:8091/api/v1/raffle/activity/draw">/api/v1/raffle/activity/draw</a>
     * 入参：{"activityId":100001,"userId":"xiaofuge"}
     * <p>
     * curl --request POST \
     * --url http://localhost:8091/api/v1/raffle/activity/draw \
     * --header 'content-type: application/json' \
     * --data '{
     * "userId":"xiaofuge",
     * "activityId": 100301
     * }'
     * 限流配置
     * RateLimiterAccessInterceptor
     * key: 以用户ID作为拦截，这个用户访问次数限制
     * fallbackMethod：失败后的回调方法，方法出入参保持一样
     * permitsPerSecond：每秒的访问频次限制
     * blacklistCount：超过多少次都被限制了，还访问的，扔到黑名单里24小时
     */
    @RateLimiterAccessInterceptor(key = "userId", fallbackMethod = "drawRateLimiterError", permitsPerSecond = 1.0d, blacklistCount = 1)
    @HystrixCommand(commandProperties = {
            @HystrixProperty(name = "execution.isolation.thread.timeoutInMilliseconds", value = "150")
    }, fallbackMethod = "drawHystrixError"
    )
    @RequestMapping(value = "draw", method = RequestMethod.POST)
    @Override
    public Response<ActivityDrawResponseDTO> draw(@RequestBody ActivityDrawRequestDTO request) {
        try {
            log.info("活动抽奖开始 userId:{} activityId:{}", request.getUserId(), request.getActivityId());

            // 1. 降级开关【open 开启、close 关闭】
            if (StringUtils.isNotBlank(degradeSwitch) && "open".equals(degradeSwitch)) {
                return Response.<ActivityDrawResponseDTO>builder()
                        .code(ResponseCode.DEGRADE_SWITCH.getCode())
                        .info(ResponseCode.DEGRADE_SWITCH.getInfo())
                        .build();
            }

            // 2. 委托应用服务执行抽奖
            ActivityDrawResponseEntity result = raffleApplicationService.executeDraw(
                    ActivityDrawRequestEntity.builder()
                            .userId(request.getUserId())
                            .activityId(request.getActivityId())
                            .build());

            return Response.<ActivityDrawResponseDTO>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(ActivityDrawResponseDTO.builder()
                            .awardId(result.getAwardId())
                            .awardTitle(result.getAwardTitle())
                            .awardIndex(result.getAwardIndex())
                            .build())
                    .build();
        } catch (AppException e) {
            log.error("活动抽奖失败 userId:{} activityId:{}", request.getUserId(), request.getActivityId(), e);
            return Response.<ActivityDrawResponseDTO>builder()
                    .code(e.getCode())
                    .info(e.getInfo())
                    .build();
        } catch (Exception e) {
            log.error("活动抽奖失败 userId:{} activityId:{}", request.getUserId(), request.getActivityId(), e);
            return Response.<ActivityDrawResponseDTO>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

    public Response<ActivityDrawResponseDTO> drawRateLimiterError(@RequestBody ActivityDrawRequestDTO request) {
        log.info("活动抽奖限流 userId:{} activityId:{}", request.getUserId(), request.getActivityId());
        return Response.<ActivityDrawResponseDTO>builder()
                .code(ResponseCode.RATE_LIMITER.getCode())
                .info(ResponseCode.RATE_LIMITER.getInfo())
                .build();
    }

    public Response<ActivityDrawResponseDTO> drawHystrixError(@RequestBody ActivityDrawRequestDTO request) {
        log.info("活动抽奖熔断 userId:{} activityId:{}", request.getUserId(), request.getActivityId());
        return Response.<ActivityDrawResponseDTO>builder()
                .code(ResponseCode.HYSTRIX.getCode())
                .info(ResponseCode.HYSTRIX.getInfo())
                .build();
    }

    @RequestMapping(value = "calendar_sign_rebate_by_token", method = RequestMethod.POST)
    @Override
    public Response<Boolean> calendarSignRebateByToken(@RequestHeader("Authorization") String token) {
        try {
            String openid = (String) httpServletRequest.getAttribute("userId");
            if (StringUtils.isBlank(openid)) {
                return Response.<Boolean>builder()
                        .code(ResponseCode.Login.TOKEN_ERROR.getCode())
                        .info(ResponseCode.Login.TOKEN_ERROR.getInfo())
                        .build();
            }
            log.info("执行签到开始 userId:{}", openid);
            return calendarSignRebate(openid);
        } catch (Exception e) {
            log.error("执行签到失败", e);
            return Response.<Boolean>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

    /**
     * 日历签到返利接口
     *
     * @param userId 用户ID
     * @return 签到返利结果
     * <p>
     * 接口：<a href="http://localhost:8091/api/v1/raffle/activity/calendar_sign_rebate">/api/v1/raffle/activity/calendar_sign_rebate</a>
     * 入参：xiaofuge
     * <p>
     * curl -X POST http://localhost:8091/api/v1/raffle/activity/calendar_sign_rebate -d "userId=xiaofuge" -H "Content-Type: application/x-www-form-urlencoded"
     */
    @RequestMapping(value = "calendar_sign_rebate", method = RequestMethod.POST)
    @Override
    public Response<Boolean> calendarSignRebate(@RequestParam String userId) {
        try {
            log.info("日历签到返利开始 userId:{}", userId);
            if (StringUtils.isBlank(userId)) {
                throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), ResponseCode.ILLEGAL_PARAMETER.getInfo());
            }
            BehaviorEntity behaviorEntity = new BehaviorEntity();
            behaviorEntity.setUserId(userId);
            behaviorEntity.setBehaviorTypeVO(BehaviorTypeVO.SIGN);
            behaviorEntity.setOutBusinessNo(LocalDate.now().format(DATE_FORMAT_DAY));
            List<String> orderIds = behaviorRebateService.createOrder(behaviorEntity);
            log.info("日历签到返利完成 userId:{} orderIds: {}", userId, JSON.toJSONString(orderIds));
            return Response.<Boolean>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(true)
                    .build();
        } catch (AppException e) {
            log.error("日历签到返利异常 userId:{} ", userId, e);
            return Response.<Boolean>builder()
                    .code(e.getCode())
                    .info(e.getInfo())
                    .build();
        } catch (Exception e) {
            log.error("日历签到返利失败 userId:{}", userId, e);
            return Response.<Boolean>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(false)
                    .build();
        }
    }

    @RequestMapping(value = "is_calendar_sign_rebate_by_token", method = RequestMethod.POST)
    @Override
    public Response<Boolean> isCalendarSignRebateByToken(@RequestHeader("Authorization") String token) {
        try {
            String openid = (String) httpServletRequest.getAttribute("userId");
            if (StringUtils.isBlank(openid)) {
                return Response.<Boolean>builder()
                        .code(ResponseCode.Login.TOKEN_ERROR.getCode())
                        .info(ResponseCode.Login.TOKEN_ERROR.getInfo())
                        .build();
            }
            log.info("执行判断签到开始 userId:{}", openid);
            return isCalendarSignRebate(openid);
        } catch (Exception e) {
            log.error("执行判断签到失败", e);
            return Response.<Boolean>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

    /**
     * 判断是否签到接口
     * <p>
     * curl -X POST http://localhost:8091/api/v1/raffle/activity/is_calendar_sign_rebate -d "userId=xiaofuge" -H "Content-Type: application/x-www-form-urlencoded"
     */
    @RequestMapping(value = "is_calendar_sign_rebate", method = RequestMethod.POST)
    @Override
    public Response<Boolean> isCalendarSignRebate(@RequestParam String userId) {
        try {
            log.info("查询用户是否完成日历签到返利开始 userId:{}", userId);
            if (StringUtils.isBlank(userId)) {
                throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), ResponseCode.ILLEGAL_PARAMETER.getInfo());
            }
            String outBusinessNo = LocalDate.now().format(DATE_FORMAT_DAY);
            List<BehaviorRebateOrderEntity> behaviorRebateOrderEntities = behaviorRebateService.queryOrderByOutBusinessNo(userId, outBusinessNo);
            log.info("查询用户是否完成日历签到返利完成 userId:{} orders.size:{}", userId, behaviorRebateOrderEntities.size());
            return Response.<Boolean>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(!behaviorRebateOrderEntities.isEmpty()) // 只要不为空，则表示已经做了签到
                    .build();
        } catch (Exception e) {
            log.error("查询用户是否完成日历签到返利失败 userId:{}", userId, e);
            return Response.<Boolean>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(false)
                    .build();
        }
    }

    @RequestMapping(value = "query_user_activity_account_by_token", method = RequestMethod.POST)
    @Override
    public Response<UserActivityAccountResponseDTO> queryUserActivityAccount(@RequestHeader("Authorization") String token, @RequestBody UserActivityAccountRequestDTO request) {
        try {
            String openid = (String) httpServletRequest.getAttribute("userId");
            if (StringUtils.isBlank(openid)) {
                return Response.<UserActivityAccountResponseDTO>builder()
                        .code(ResponseCode.Login.TOKEN_ERROR.getCode())
                        .info(ResponseCode.Login.TOKEN_ERROR.getInfo())
                        .build();
            }
            log.info("查询活动账户开始 userId:{}", openid);
            request.setUserId(openid);

            return queryUserActivityAccount(request);
        } catch (Exception e) {
            log.error("查询活动账户失败", e);
            return Response.<UserActivityAccountResponseDTO>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

    /**
     * 查询账户额度
     * <p>
     * curl --request POST \
     * --url http://localhost:8091/api/v1/raffle/activity/query_user_activity_account \
     * --header 'content-type: application/json' \
     * --data '{
     * "userId":"xiaofuge",
     * "activityId": 100301
     * }'
     */
    @RequestMapping(value = "query_user_activity_account", method = RequestMethod.POST)
    @Override
    public Response<UserActivityAccountResponseDTO> queryUserActivityAccount(@RequestBody UserActivityAccountRequestDTO request) {
        try {
            log.info("查询用户活动账户开始 userId:{} activityId:{}", request.getUserId(), request.getActivityId());
            // 1. 参数校验
            if (StringUtils.isBlank(request.getUserId()) || null == request.getActivityId()) {
                throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), ResponseCode.ILLEGAL_PARAMETER.getInfo());
            }
            ActivityAccountEntity activityAccountEntity = raffleActivityAccountQuotaService.queryActivityAccountEntity(request.getActivityId(), request.getUserId());
            UserActivityAccountResponseDTO userActivityAccountResponseDTO = UserActivityAccountResponseDTO.builder()
                    .totalCount(activityAccountEntity.getTotalCount())
                    .totalCountSurplus(activityAccountEntity.getTotalCountSurplus())
                    .dayCount(activityAccountEntity.getDayCount())
                    .dayCountSurplus(activityAccountEntity.getDayCountSurplus())
                    .monthCount(activityAccountEntity.getMonthCount())
                    .monthCountSurplus(activityAccountEntity.getMonthCountSurplus())
                    .build();
            log.info("查询用户活动账户完成 userId:{} activityId:{} dto:{}", request.getUserId(), request.getActivityId(), JSON.toJSONString(userActivityAccountResponseDTO));
            return Response.<UserActivityAccountResponseDTO>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(userActivityAccountResponseDTO)
                    .build();
        } catch (Exception e) {
            log.error("查询用户活动账户失败 userId:{} activityId:{}", request.getUserId(), request.getActivityId(), e);
            return Response.<UserActivityAccountResponseDTO>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

    @RequestMapping(value = "query_sku_product_list_by_activity_id", method = RequestMethod.POST)
    @Override
    public Response<List<SkuProductResponseDTO>> querySkuProductListByActivityId(Long activityId) {
        try {
            log.info("查询sku商品集合开始 activityId:{}", activityId);
            // 1. 参数校验
            if (null == activityId) {
                throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), ResponseCode.ILLEGAL_PARAMETER.getInfo());
            }
            // 2. 查询商品&封装数据
            List<SkuProductEntity> skuProductEntities = raffleActivitySkuProductService.querySkuProductEntityListByActivityId(activityId);
            List<SkuProductResponseDTO> skuProductResponseDTOS = new ArrayList<>(skuProductEntities.size());
            for (SkuProductEntity skuProductEntity : skuProductEntities) {

                SkuProductResponseDTO.ActivityCount activityCount = new SkuProductResponseDTO.ActivityCount();
                activityCount.setTotalCount(skuProductEntity.getActivityCount().getTotalCount());
                activityCount.setMonthCount(skuProductEntity.getActivityCount().getMonthCount());
                activityCount.setDayCount(skuProductEntity.getActivityCount().getDayCount());

                SkuProductResponseDTO skuProductResponseDTO = new SkuProductResponseDTO();
                skuProductResponseDTO.setSku(skuProductEntity.getSku());
                skuProductResponseDTO.setActivityId(skuProductEntity.getActivityId());
                skuProductResponseDTO.setActivityCountId(skuProductEntity.getActivityCountId());
                skuProductResponseDTO.setStockCount(skuProductEntity.getStockCount());
                skuProductResponseDTO.setStockCountSurplus(skuProductEntity.getStockCountSurplus());
                skuProductResponseDTO.setProductAmount(skuProductEntity.getProductAmount());
                skuProductResponseDTO.setActivityCount(activityCount);
                skuProductResponseDTOS.add(skuProductResponseDTO);
            }

            log.info("查询sku商品集合完成 activityId:{} skuProductResponseDTOS:{}", activityId, JSON.toJSONString(skuProductResponseDTOS));
            return Response.<List<SkuProductResponseDTO>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(skuProductResponseDTOS)
                    .build();
        } catch (Exception e) {
            log.error("查询sku商品集合失败 activityId:{}", activityId, e);
            return Response.<List<SkuProductResponseDTO>>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

    @RequestMapping(value = "query_user_credit_account_by_token", method = RequestMethod.POST)
    @Override
    public Response<BigDecimal> queryUserCreditAccountByToken(@RequestHeader("Authorization") String token) {
        try {
            String openid = (String) httpServletRequest.getAttribute("userId");
            if (StringUtils.isBlank(openid)) {
                return Response.<BigDecimal>builder()
                        .code(ResponseCode.Login.TOKEN_ERROR.getCode())
                        .info(ResponseCode.Login.TOKEN_ERROR.getInfo())
                        .build();
            }
            log.info("查询用户积分值开始 userId:{}", openid);

            return queryUserCreditAccount(openid);
        } catch (Exception e) {
            log.error("查询用户积分值失败", e);
            return Response.<BigDecimal>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

    @RequestMapping(value = "query_user_credit_account", method = RequestMethod.POST)
    @Override
    public Response<BigDecimal> queryUserCreditAccount(String userId) {
        try {
            log.info("查询用户积分值开始 userId:{}", userId);
            if (StringUtils.isBlank(userId)) {
                throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), ResponseCode.ILLEGAL_PARAMETER.getInfo());
            }
            CreditAccountEntity creditAccountEntity = creditAdjustService.queryUserCreditAccount(userId);
            log.info("查询用户积分值完成 userId:{} adjustAmount:{}", userId, creditAccountEntity.getAdjustAmount());
            return Response.<BigDecimal>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(creditAccountEntity.getAdjustAmount())
                    .build();
        } catch (Exception e) {
            log.error("查询用户积分值失败 userId:{}", userId, e);
            return Response.<BigDecimal>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

    @RequestMapping(value = "credit_pay_exchange_sku_by_token", method = RequestMethod.POST)
    @Override
    public Response<Boolean> creditPayExchangeSku(@RequestHeader("Authorization") String token, @RequestBody SkuProductShopCartRequestDTO request) {
        try {
            String openid = (String) httpServletRequest.getAttribute("userId");
            if (StringUtils.isBlank(openid)) {
                return Response.<Boolean>builder()
                        .code(ResponseCode.Login.TOKEN_ERROR.getCode())
                        .info(ResponseCode.Login.TOKEN_ERROR.getInfo())
                        .build();
            }
            log.info("积分兑换开始 userId:{}", openid);
            request.setUserId(openid);

            return creditPayExchangeSku(request);
        } catch (Exception e) {
            log.error("积分兑换失败", e);
            return Response.<Boolean>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }


    @RequestMapping(value = "credit_pay_exchange_sku", method = RequestMethod.POST)
    @Override
    public Response<Boolean> creditPayExchangeSku(@RequestBody SkuProductShopCartRequestDTO request) {
        try {
            log.info("积分兑换商品开始 userId:{} sku:{}", request.getUserId(), request.getSku());
            // 0. 参数校验
            if (StringUtils.isBlank(request.getUserId())) {
                throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), ResponseCode.ILLEGAL_PARAMETER.getInfo());
            }
            // 1. 创建兑换商品sku订单，outBusinessNo 每次创建出一个单号。
            UnpaidActivityOrderEntity unpaidActivityOrder = raffleActivityAccountQuotaService.createOrder(SkuRechargeEntity.builder()
                    .userId(request.getUserId())
                    .sku(request.getSku())
                    .outBusinessNo(RandomStringUtils.randomNumeric(12))
                    .orderTradeType(OrderTradeTypeVO.credit_pay_trade)
                    .build());
            log.info("积分兑换商品，创建订单完成 userId:{} sku:{} outBusinessNo:{}", request.getUserId(), request.getSku(), unpaidActivityOrder.getOutBusinessNo());

            // 2.支付兑换商品
            String orderId = creditAdjustService.createOrder(TradeEntity.builder()
                    .userId(unpaidActivityOrder.getUserId())
                    .tradeName(TradeNameVO.CONVERT_SKU)
                    .tradeType(TradeTypeVO.REVERSE)
                    .amount(unpaidActivityOrder.getPayAmount().negate())
                    .outBusinessNo(unpaidActivityOrder.getOutBusinessNo())
                    .build());
            log.info("积分兑换商品，支付订单完成  userId:{} sku:{} orderId:{}", request.getUserId(), request.getSku(), orderId);

            return Response.<Boolean>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(true)
                    .build();
        } catch (AppException e) {
            log.error("积分兑换商品失败 userId:{} activityId:{}", request.getUserId(), request.getSku(), e);
            return Response.<Boolean>builder()
                    .code(e.getCode())
                    .info(e.getInfo())
                    .build();
        } catch (Exception e) {
            log.error("积分兑换商品失败 userId:{} sku:{}", request.getUserId(), request.getSku(), e);
            return Response.<Boolean>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(false)
                    .build();
        }
    }

}

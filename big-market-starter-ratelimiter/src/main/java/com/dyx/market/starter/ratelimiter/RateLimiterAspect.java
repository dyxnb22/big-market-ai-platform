package com.dyx.market.starter.ratelimiter;

import com.dyx.market.types.annotations.RateLimiterAccessInterceptor;
import com.dyx.market.types.config.RuntimeConfigHolder;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.util.concurrent.RateLimiter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

/**
 * 基于 Guava RateLimiter 的接口限流切面。
 *
 * <p>拦截 {@link RateLimiterAccessInterceptor} 标注的方法，
 * 按 key 维度限流；超限时可调用 fallback 方法或进入黑名单。</p>
 */
@Slf4j
@Aspect
public class RateLimiterAspect {

    private final RuntimeConfigHolder runtimeConfigHolder;

    /** 各限流 key 对应的令牌桶，1 分钟无访问后过期。 */
    private final Cache<String, RateLimiter> accessRecord = CacheBuilder.newBuilder()
            .expireAfterWrite(1, TimeUnit.MINUTES)
            .build();

    /** 超限计数黑名单，24 小时后过期。 */
    private final Cache<String, Long> blacklist = CacheBuilder.newBuilder()
            .expireAfterWrite(24, TimeUnit.HOURS)
            .build();

    public RateLimiterAspect(RuntimeConfigHolder runtimeConfigHolder) {
        this.runtimeConfigHolder = runtimeConfigHolder;
    }

    @Pointcut("@annotation(com.dyx.market.types.annotations.RateLimiterAccessInterceptor)")
    public void rateLimiterPoint() {
    }

    /**
     * 限流拦截：开关关闭则放行；否则按 key 限流，超限走 fallback 或黑名单。
     */
    @Around("rateLimiterPoint() && @annotation(rateLimiterAccessInterceptor)")
    public Object intercept(ProceedingJoinPoint jp, RateLimiterAccessInterceptor rateLimiterAccessInterceptor) throws Throwable {
        if (!runtimeConfigHolder.isRateLimiterEnabled()) {
            return jp.proceed();
        }

        String key = rateLimiterAccessInterceptor.key();
        if (StringUtils.isBlank(key)) {
            throw new RuntimeException("RateLimiter key is blank");
        }

        String accessKey = getAttrValue(key, jp.getArgs());
        if (isBlockedByBlacklist(accessKey, rateLimiterAccessInterceptor)) {
            log.info("RateLimiter blacklist blocked: {}", accessKey);
            return fallbackMethodResult(jp, rateLimiterAccessInterceptor.fallbackMethod());
        }

        RateLimiter rateLimiter = accessRecord.getIfPresent(accessKey);
        if (rateLimiter == null) {
            rateLimiter = RateLimiter.create(rateLimiterAccessInterceptor.permitsPerSecond());
            accessRecord.put(accessKey, rateLimiter);
        }

        if (!rateLimiter.tryAcquire()) {
            addBlacklistCount(accessKey, rateLimiterAccessInterceptor.blacklistCount());
            log.info("RateLimiter frequency blocked: {}", accessKey);
            return fallbackMethodResult(jp, rateLimiterAccessInterceptor.fallbackMethod());
        }

        return jp.proceed();
    }

    private boolean isBlockedByBlacklist(String accessKey, RateLimiterAccessInterceptor interceptor) {
        Long count = blacklist.getIfPresent(accessKey);
        return !"all".equals(accessKey)
                && interceptor.blacklistCount() != 0
                && count != null
                && count > interceptor.blacklistCount();
    }

    private void addBlacklistCount(String accessKey, double blacklistCount) {
        if (blacklistCount == 0) {
            return;
        }
        Long count = blacklist.getIfPresent(accessKey);
        blacklist.put(accessKey, count == null ? 1L : count + 1L);
    }

    private Object fallbackMethodResult(JoinPoint jp, String fallbackMethod) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Signature sig = jp.getSignature();
        MethodSignature methodSignature = (MethodSignature) sig;
        Method method = jp.getTarget().getClass().getMethod(fallbackMethod, methodSignature.getParameterTypes());
        return method.invoke(jp.getThis(), jp.getArgs());
    }

    private String getAttrValue(String attr, Object[] args) {
        if (args == null || args.length == 0) {
            return "all";
        }
        if (args[0] instanceof String) {
            return args[0].toString();
        }
        for (Object arg : args) {
            Object value = getValueByName(arg, attr);
            if (value != null && StringUtils.isNotBlank(String.valueOf(value))) {
                return String.valueOf(value);
            }
        }
        return "all";
    }

    private Object getValueByName(Object item, String name) {
        if (item == null) {
            return null;
        }
        try {
            Field field = getFieldByName(item, name);
            if (field == null) {
                return null;
            }
            field.setAccessible(true);
            Object value = field.get(item);
            field.setAccessible(false);
            return value;
        } catch (IllegalAccessException e) {
            return null;
        }
    }

    private Field getFieldByName(Object item, String name) {
        try {
            try {
                return item.getClass().getDeclaredField(name);
            } catch (NoSuchFieldException e) {
                return item.getClass().getSuperclass().getDeclaredField(name);
            }
        } catch (NoSuchFieldException e) {
            return null;
        }
    }
}

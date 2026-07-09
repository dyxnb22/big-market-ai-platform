package com.dyx.market.types.annotations;

import java.lang.annotation.*;

/**
 * 方法级限流注解：声明 key 维度、每秒许可数、黑名单阈值与超限 fallback 方法名。
 *
 * <p>由 big-market-starter-ratelimiter 的 {@code RateLimiterAspect} 在运行时拦截执行。</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
@Documented
public @interface RateLimiterAccessInterceptor {

    /** 用哪个字段作为拦截标识，未配置则默认走全部 */
    String key() default "all";

    /** 限制频次（每秒请求次数） */
    double permitsPerSecond();

    /** 黑名单拦截（多少次限制后加入黑名单）0 不限制 */
    double blacklistCount() default 0;

    /** 拦截后的执行方法 */
    String fallbackMethod();

}

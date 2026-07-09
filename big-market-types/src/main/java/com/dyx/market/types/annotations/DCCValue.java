package com.dyx.market.types.annotations;

import java.lang.annotation.*;

/**
 * 动态配置中心字段注解：{@code value} 为 {@code 配置键:默认值}。
 *
 * <p>由 big-market-starter-dcc 在 Bean 初始化时注入，并在 ZK 节点变更时热更新字段值。</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD})
@Documented
public @interface DCCValue {

    String value() default "";

}

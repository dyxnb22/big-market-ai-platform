package com.dyx.market.middleware.db.router.annotation;

import java.lang.annotation.*;

/**
 * 标记需要进行用户分片路由计算的 DAO 方法。
 *
 * <p>默认从第一个方法参数中提取配置的路由键。
 * 在本项目中，常用的路由键为 {@code userId}。</p>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface DBRouter {

    /** 路由键字段名，为空时从方法第一个参数取值。 */
    String key() default "";

}

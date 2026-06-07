package com.dyx.market.middleware.db.router.annotation;

import java.lang.annotation.*;

/**
 * Marks a DAO method that needs user-sharding route calculation.
 *
 * <p>The router extracts the configured key from the first argument by default.
 * In this project the usual route key is {@code userId}.</p>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface DBRouter {

    String key() default "";

}
